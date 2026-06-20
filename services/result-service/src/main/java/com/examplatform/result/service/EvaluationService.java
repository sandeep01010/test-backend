package com.examplatform.result.service;

import com.examplatform.result.model.Result;
import com.examplatform.result.repository.ResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

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
    private final RestTemplate restTemplate;

    @Value("${services.exam-service-url:http://exam-service:8082}")
    private String examServiceUrl;

    private static final String ANSWER_KEY_CACHE = "exam:answerkey:%s"; // examId

    /**
     * Evaluate a submitted session:
     * 1. Fetch final answers from MongoDB (answer_snapshots, keyed by _id = sessionId)
     * 2. Load answer key for each answered question from MongoDB questions collection
     * 3. Score: +marks for correct, -negativeMarks for wrong, 0 for skip
     * 4. Persist result to PostgreSQL
     */
    @Transactional
    public void evaluateSubmission(String sessionId, String examId) {
        log.info("Evaluating session {} for exam {}", sessionId, examId);

        // 1. Fetch answer snapshot — sessionId stored as MongoDB _id
        Document snapshot = fetchAnswerSnapshot(sessionId);
        if (snapshot == null) {
            log.error("No answer snapshot found for session {}", sessionId);
            return;
        }

        String studentId    = snapshot.getString("student_id");
        String enrollmentId = snapshot.getString("enrollment_id");

        if (studentId == null || enrollmentId == null) {
            log.error("Snapshot for session {} missing studentId or enrollmentId", sessionId);
            return;
        }

        // 2. Fetch exam config from exam-service to get authoritative totalQuestions + totalMarks
        ExamConfig examConfig = fetchExamConfig(examId);
        int totalQuestionsInExam = examConfig.totalQuestions();
        double totalMarksInExam  = examConfig.totalMarks();

        // 3. Extract answers map: questionId → {answer: [...], ...}
        Document answersDoc = snapshot.get("answers", Document.class);
        if (answersDoc == null || answersDoc.isEmpty()) {
            log.warn("No answers found in snapshot for session {}", sessionId);
            saveResult(sessionId, enrollmentId, studentId, examId,
                    new EvaluationResult(0, Map.of(), 0, 0, totalQuestionsInExam),
                    totalMarksInExam);
            return;
        }

        // 4. Load answer key for all answered question IDs
        Set<String> questionIds = answersDoc.keySet();
        Map<String, QuestionKey> answerKey = loadAnswerKeyForQuestions(questionIds);

        // 5. Score — only answered questions; skipped = rest of exam
        EvaluationResult evalResult = scoreAnswers(answersDoc, answerKey, totalQuestionsInExam);

        // 6. Save
        saveResult(sessionId, enrollmentId, studentId, examId, evalResult, totalMarksInExam);
        log.info("Result saved for session {} student {} score {}", sessionId, studentId, evalResult.totalScore());
    }

    /**
     * Compute ranks and percentiles for all students in an exam.
     * Called by admin after all submissions are processed.
     */
    @Transactional
    public void computeRanks(UUID examId) {
        log.info("Computing ranks for exam {}", examId);

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

        jdbcTemplate.update("""
            UPDATE results
            SET status = 'PUBLISHED', published_at = NOW()
            WHERE exam_id = ? AND status = 'EVALUATED'
            """, examId);

        kafkaTemplate.send("result-events", examId.toString(),
                Map.of("event", "RESULTS_PUBLISHED", "examId", examId, "timestamp", Instant.now().toString()));

        log.info("Ranks computed and results published for exam {}", examId);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /** Fetch the answer snapshot from MongoDB. sessionId is stored as _id. */
    private Document fetchAnswerSnapshot(String sessionId) {
        Query query = new Query(Criteria.where("_id").is(sessionId));
        return mongoTemplate.findOne(query, Document.class, "answer_snapshots");
    }

    /**
     * Load question metadata (correct answer, marks, section) from MongoDB
     * for all question IDs the student answered.
     */
    private Map<String, QuestionKey> loadAnswerKeyForQuestions(Set<String> questionIds) {
        if (questionIds.isEmpty()) return Map.of();

        Query query = new Query(Criteria.where("_id").in(questionIds));
        List<Document> questions = mongoTemplate.find(query, Document.class, "questions");

        Map<String, QuestionKey> key = new HashMap<>();
        for (Document q : questions) {
            String qId          = q.getObjectId("_id") != null
                                    ? q.getObjectId("_id").toString()
                                    : q.getString("_id");
            String correctAnswer = q.getString("correct_answer");
            double marks         = getDouble(q, "marks", 4.0);
            double negMarks      = Math.abs(getDouble(q, "negative_marks", 1.0));
            String subject       = q.getString("subject");
            String type          = q.getString("type");

            key.put(qId, new QuestionKey(correctAnswer, marks, negMarks,
                    subject != null ? subject : "GENERAL", type));
        }
        return key;
    }

    private EvaluationResult scoreAnswers(Document answersDoc,
                                          Map<String, QuestionKey> answerKey,
                                          int totalQuestionsInExam) {
        double totalScore = 0;
        int correct = 0, wrong = 0;
        Map<String, Double> sectionScores = new HashMap<>();

        for (String questionId : answersDoc.keySet()) {
            Document answerEntry = answersDoc.get(questionId, Document.class);
            QuestionKey key      = answerKey.get(questionId);

            List<?> answerList = answerEntry != null
                    ? answerEntry.getList("answer", String.class) : null;
            boolean isSkipped  = answerList == null || answerList.isEmpty();

            if (isSkipped) continue; // unanswered entries are counted in skipped below

            if (key == null) {
                // Answer key missing — can't evaluate; treat as wrong (student attempted it)
                log.warn("No answer key found for question {} — treating as wrong", questionId);
                wrong++;
                continue;
            }

            boolean isCorrect = evaluate(answerList, key);
            if (isCorrect) {
                totalScore += key.marks();
                sectionScores.merge(key.subject(), key.marks(), Double::sum);
                correct++;
            } else {
                totalScore -= key.negMarks();
                sectionScores.merge(key.subject(), -key.negMarks(), Double::sum);
                wrong++;
            }
        }

        // Skipped = all questions in the paper that were not attempted
        // (includes both questions not in snapshot AND questions in snapshot with empty answer)
        int attempted = correct + wrong;
        int skipped   = Math.max(0, totalQuestionsInExam - attempted);

        return new EvaluationResult(totalScore, sectionScores, correct, wrong, skipped);
    }

    private boolean evaluate(List<?> studentAnswers, QuestionKey key) {
        if (studentAnswers.isEmpty()) return false;

        String correctAnswer = key.correctAnswer();
        if (correctAnswer == null || correctAnswer.isBlank()) return false;

        if ("NUMERICAL".equalsIgnoreCase(key.type())) {
            try {
                double studentVal = Double.parseDouble(studentAnswers.get(0).toString().trim());
                double correctVal = Double.parseDouble(correctAnswer.trim());
                return Math.abs(studentVal - correctVal) < 0.01;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // MCQ_SINGLE / MCQ_MULTIPLE: compare as sorted sets
        List<String> studentSorted = studentAnswers.stream()
                .map(Object::toString).map(String::toUpperCase).sorted().toList();
        List<String> correctSorted = Arrays.stream(correctAnswer.split("[,|]"))
                .map(String::trim).map(String::toUpperCase).filter(s -> !s.isEmpty())
                .sorted().toList();

        return studentSorted.equals(correctSorted);
    }

    private void saveResult(String sessionId, String enrollmentId, String studentId,
                            String examId, EvaluationResult evalResult, double totalMarks) {
        UUID examUuid       = UUID.fromString(examId);
        UUID studentUuid    = UUID.fromString(studentId);
        UUID enrollmentUuid = UUID.fromString(enrollmentId);

        int priorAttempts = resultRepository.countByStudentIdAndExamId(studentUuid, examUuid);

        Result result = Result.builder()
                .sessionId(UUID.fromString(sessionId))
                .enrollmentId(enrollmentUuid)
                .studentId(studentUuid)
                .examId(examUuid)
                .totalScore(evalResult.totalScore())
                .totalMarks(totalMarks)
                .attemptNumber(priorAttempts + 1)
                .sectionScores(evalResult.sectionScores())
                .correctCount(evalResult.correctCount())
                .wrongCount(evalResult.wrongCount())
                .skippedCount(evalResult.skippedCount())
                .status(Result.ResultStatus.EVALUATED)
                .submittedAt(Instant.now())
                .evaluatedAt(Instant.now())
                .build();
        resultRepository.save(result);
    }

    /**
     * Fetch exam config (totalQuestions, totalMarks) from the exam-service.
     * Falls back to computing from the questions MongoDB collection if the call fails.
     */
    private ExamConfig fetchExamConfig(String examId) {
        try {
            Map<?, ?> exam = restTemplate.getForObject(
                    examServiceUrl + "/api/v1/exams/" + examId, Map.class);
            if (exam != null) {
                int totalQ = exam.get("totalQuestions") instanceof Number n ? n.intValue() : 0;
                double totalM = exam.get("totalMarks") instanceof Number n ? n.doubleValue() : 0;
                if (totalQ > 0) {
                    log.debug("Exam config from exam-service: {} questions, {} marks", totalQ, totalM);
                    return new ExamConfig(totalQ, totalM);
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch exam config for {}: {}", examId, e.getMessage());
        }
        // Fallback: compute from questions collection (may be incomplete)
        try {
            Query query = new Query(Criteria.where("exam_id").is(examId));
            List<Document> questions = mongoTemplate.find(query, Document.class, "questions");
            if (!questions.isEmpty()) {
                double marks = questions.stream().mapToDouble(q -> getDouble(q, "marks", 4.0)).sum();
                return new ExamConfig(questions.size(), marks);
            }
        } catch (Exception ignored) {}
        return new ExamConfig(0, 0);
    }

    private double getDouble(Document doc, String field, double defaultVal) {
        Object v = doc.get(field);
        if (v == null) return defaultVal;
        try { return ((Number) v).doubleValue(); } catch (ClassCastException e) { return defaultVal; }
    }

    private record ExamConfig(int totalQuestions, double totalMarks) {}

    private record QuestionKey(
        String correctAnswer,
        double marks,
        double negMarks,
        String subject,
        String type
    ) {}

    private record EvaluationResult(
        double totalScore,
        Map<String, Double> sectionScores,
        int correctCount,
        int wrongCount,
        int skippedCount
    ) {}
}
