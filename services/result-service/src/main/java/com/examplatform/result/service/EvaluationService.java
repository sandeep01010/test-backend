package com.examplatform.result.service;

import com.examplatform.result.model.Result;
import com.examplatform.result.repository.ResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final ResultRepository resultRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String ANSWER_KEY_CACHE = "exam:answerkey:%s"; // examId

    /**
     * Evaluate a submitted session:
     * 1. Fetch final answers from MongoDB
     * 2. Fetch answer key from Redis (pre-loaded before exam)
     * 3. Score: +marks for correct, -negativeMarks for wrong, 0 for skip
     * 4. Persist result to PostgreSQL
     */
    @Transactional
    public void evaluateSubmission(String sessionId, String examId) {
        log.info("Evaluating session {} for exam {}", sessionId, examId);

        // Fetch answer snapshot from MongoDB
        Map<String, Object> snapshot = fetchAnswerSnapshot(sessionId);
        if (snapshot == null) {
            log.error("No answer snapshot found for session {}", sessionId);
            return;
        }

        // Fetch answer key from Redis
        Map<String, Object> answerKey = fetchAnswerKey(examId);
        if (answerKey == null || answerKey.isEmpty()) {
            log.warn("Answer key not cached for exam {}, loading from DB", examId);
            answerKey = loadAnswerKeyFromDB(examId);
        }

        // Score computation
        EvaluationResult evalResult = scoreAnswers(snapshot, answerKey);

        // Fetch enrollment info for linking
        String enrollmentId = (String) snapshot.get("enrollmentId");
        String studentId = (String) snapshot.get("studentId");

        // Save result
        Result result = Result.builder()
                .enrollmentId(UUID.fromString(enrollmentId))
                .studentId(UUID.fromString(studentId))
                .examId(UUID.fromString(examId))
                .totalScore(evalResult.totalScore())
                .sectionScores(evalResult.sectionScores())
                .correctCount(evalResult.correctCount())
                .wrongCount(evalResult.wrongCount())
                .skippedCount(evalResult.skippedCount())
                .status(Result.ResultStatus.EVALUATED)
                .evaluatedAt(Instant.now())
                .build();

        resultRepository.save(result);
        log.info("Result saved for session {} student {} score {}",
                sessionId, studentId, evalResult.totalScore());
    }

    /**
     * Compute ranks and percentiles for all students in an exam.
     * Uses PostgreSQL window functions for efficiency.
     * Called by admin after all submissions are processed.
     */
    @Transactional
    public void computeRanks(UUID examId) {
        log.info("Computing ranks for exam {}", examId);

        // Update rank using window function — handles ties correctly
        jdbcTemplate.update("""
            UPDATE results r
            SET rank = ranked.exam_rank,
                percentile = ranked.pct
            FROM (
                SELECT id,
                    RANK() OVER (PARTITION BY exam_id ORDER BY total_score DESC) AS exam_rank,
                    ROUND(CAST(PERCENT_RANK() OVER (PARTITION BY exam_id ORDER BY total_score) * 100 AS numeric), 3) AS pct
                FROM results
                WHERE exam_id = ? AND status = 'EVALUATED'
            ) ranked
            WHERE r.id = ranked.id
            """, examId);

        // Mark results as published
        jdbcTemplate.update("""
            UPDATE results
            SET status = 'PUBLISHED', published_at = NOW()
            WHERE exam_id = ? AND status = 'EVALUATED'
            """, examId);

        // Publish results-ready event
        kafkaTemplate.send("result-events", examId.toString(),
                Map.of("event", "RESULTS_PUBLISHED", "examId", examId, "timestamp", Instant.now().toString()));

        log.info("Ranks computed and results published for exam {}", examId);
    }

    private EvaluationResult scoreAnswers(Map<String, Object> snapshot, Map<String, Object> answerKey) {
        @SuppressWarnings("unchecked")
        Map<String, Object> answers = (Map<String, Object>) snapshot.get("answers");

        double totalScore = 0;
        int correct = 0, wrong = 0, skipped = 0;
        Map<String, Double> sectionScores = new HashMap<>();

        if (answers == null) {
            return new EvaluationResult(0, Map.of(), 0, 0, 0);
        }

        for (Map.Entry<String, Object> entry : answers.entrySet()) {
            String questionId = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> answerEntry = (Map<String, Object>) entry.getValue();
            String studentAnswer = answerEntry != null ? (String) answerEntry.get("answer") : null;

            @SuppressWarnings("unchecked")
            Map<String, Object> keyEntry = (Map<String, Object>) answerKey.get(questionId);
            if (keyEntry == null) continue;

            String correctAnswer = (String) keyEntry.get("correctAnswer");
            double marks = ((Number) keyEntry.getOrDefault("marks", 4.0)).doubleValue();
            double negMarks = ((Number) keyEntry.getOrDefault("negativeMarks", 1.0)).doubleValue();
            String section = (String) keyEntry.getOrDefault("section", "GENERAL");

            if (studentAnswer == null || studentAnswer.isBlank()) {
                skipped++;
            } else if (studentAnswer.equals(correctAnswer)) {
                totalScore += marks;
                sectionScores.merge(section, marks, Double::sum);
                correct++;
            } else {
                totalScore -= negMarks;
                sectionScores.merge(section, -negMarks, Double::sum);
                wrong++;
            }
        }

        return new EvaluationResult(totalScore, sectionScores, correct, wrong, skipped);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchAnswerSnapshot(String sessionId) {
        Query query = new Query(Criteria.where("session_id").is(sessionId));
        Map result = mongoTemplate.findOne(query, Map.class, "answer_snapshots");
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchAnswerKey(String examId) {
        Object cached = redisTemplate.opsForValue()
                .get(String.format(ANSWER_KEY_CACHE, examId));
        if (cached instanceof Map) return (Map<String, Object>) cached;
        return null;
    }

    private Map<String, Object> loadAnswerKeyFromDB(String examId) {
        // Load from MongoDB question collection + exam section configuration
        // Implementation: query questions collection for questions in this exam's papers
        // and build answer key map
        log.warn("Loading answer key from DB for exam {} (this is slow — pre-cache this)", examId);
        return Map.of(); // Placeholder — implement with actual DB query
    }

    private record EvaluationResult(
        double totalScore,
        Map<String, Double> sectionScores,
        int correctCount,
        int wrongCount,
        int skippedCount
    ) {}
}
