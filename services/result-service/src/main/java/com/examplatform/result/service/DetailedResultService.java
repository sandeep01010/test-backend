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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetailedResultService {

    private final ResultRepository resultRepository;
    private final MongoTemplate mongoTemplate;
    private final RestTemplate restTemplate;

    @Value("${services.exam-service-url:http://exam-service:8082}")
    private String examServiceUrl;

    public Map<String, Object> getDetailedResult(UUID examId, UUID studentId, UUID sessionId) {
        // Find the result record
        Result result = findResult(examId, studentId, sessionId);

        // Fetch answer snapshot
        String resolvedSessionId = result.getSessionId() != null
                ? result.getSessionId().toString()
                : (sessionId != null ? sessionId.toString() : null);

        Document snapshot = resolvedSessionId != null ? fetchSnapshot(resolvedSessionId) : null;

        // Build question reviews
        List<Map<String, Object>> questions = buildQuestionReviews(snapshot, result);

        // Compute totalMarks
        double totalMarks = result.getTotalMarks() != null && result.getTotalMarks() > 0
                ? result.getTotalMarks()
                : questions.stream().mapToDouble(q -> (Double) q.getOrDefault("marks", 4.0)).sum();

        double totalScore = result.getTotalScore() != null ? result.getTotalScore() : 0;
        double percentage = totalMarks > 0 ? (totalScore / totalMarks) * 100 : 0;

        // Fetch exam title
        String examTitle = fetchExamTitle(examId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id",            result.getId().toString());
        response.put("examId",        examId.toString());
        response.put("studentId",     studentId.toString());
        response.put("examTitle",     examTitle);
        response.put("totalScore",    totalScore);
        response.put("totalMarks",    totalMarks);
        response.put("percentage",    Math.round(percentage * 10.0) / 10.0);
        response.put("correctCount",  result.getCorrectCount() != null ? result.getCorrectCount() : 0);
        response.put("wrongCount",    result.getWrongCount()   != null ? result.getWrongCount()   : 0);
        response.put("skippedCount",  result.getSkippedCount() != null ? result.getSkippedCount() : 0);
        response.put("rank",          result.getRank() != null ? result.getRank() : 0);
        response.put("percentile",    result.getPercentile() != null ? result.getPercentile() : 0.0);
        response.put("sectionScores", result.getSectionScores() != null ? result.getSectionScores() : Map.of());
        response.put("status",        result.getStatus() != null ? result.getStatus().name() : "EVALUATED");
        response.put("submittedAt",   result.getSubmittedAt()  != null ? result.getSubmittedAt().toString()
                                    : result.getEvaluatedAt() != null ? result.getEvaluatedAt().toString() : "");
        response.put("questions",     questions);
        return response;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private Result findResult(UUID examId, UUID studentId, UUID sessionId) {
        // Exact session lookup — do NOT fall back if not found yet (evaluation may still be in-flight)
        if (sessionId != null) {
            return resultRepository.findBySessionId(sessionId)
                    .orElseThrow(() -> new RuntimeException("Result not ready"));
        }
        // No sessionId: return latest attempt for this student+exam
        return resultRepository
                .findByStudentIdAndExamIdOrderByAttemptNumberAsc(studentId, examId)
                .stream()
                .max(Comparator.comparingInt(r -> r.getAttemptNumber() != null ? r.getAttemptNumber() : 0))
                .orElseThrow(() -> new RuntimeException("Result not found"));
    }

    private Document fetchSnapshot(String sessionId) {
        Query q = new Query(Criteria.where("_id").is(sessionId));
        return mongoTemplate.findOne(q, Document.class, "answer_snapshots");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildQuestionReviews(Document snapshot, Result result) {
        // Fetch ALL question IDs for this student's paper from exam-service
        List<Document> allQuestions = fetchPaperQuestions(result);

        // Build answer lookup from snapshot
        Document answersDoc = snapshot != null ? snapshot.get("answers", Document.class) : null;

        List<Map<String, Object>> reviews = new ArrayList<>();
        int order = 1;
        for (Document qDoc : allQuestions) {
            String questionId = qDoc.getObjectId("_id") != null
                    ? qDoc.getObjectId("_id").toString()
                    : qDoc.getString("_id");

            Document answerEntry = (answersDoc != null) ? answersDoc.get(questionId, Document.class) : null;

            List<String> studentAnswers = answerEntry != null
                    ? answerEntry.getList("answer", String.class)
                    : List.of();
            boolean skipped = studentAnswers == null || studentAnswers.isEmpty();

            String type = qDoc.getString("type") != null ? qDoc.getString("type") : "MCQ";
            double marks = getDouble(qDoc, "marks", 4.0);
            double negMarks = Math.abs(getDouble(qDoc, "negative_marks", 1.0));
            String correctAnswer = qDoc.getString("correct_answer");

            List<String> correctAnswerList = correctAnswer != null
                    ? List.of(correctAnswer.split("[,|]"))
                    : List.of();

            String status;
            double marksAwarded;
            if (skipped) {
                status = "SKIPPED";
                marksAwarded = 0;
            } else {
                boolean correct = evaluate(studentAnswers, correctAnswer, type);
                if (correct) {
                    status = "CORRECT";
                    marksAwarded = marks;
                } else {
                    status = "WRONG";
                    marksAwarded = -negMarks;
                }
            }

            Map<String, Object> review = new LinkedHashMap<>();
            review.put("questionId",    questionId);
            review.put("order",         order++);
            review.put("subject",       qDoc.getString("subject") != null ? qDoc.getString("subject") : "GENERAL");
            review.put("type",          type);
            review.put("status",        status);
            review.put("marks",         marks);
            review.put("negativeMarks", negMarks);
            review.put("marksAwarded",  marksAwarded);
            review.put("timeSpentSecs", 0);
            review.put("yourAnswer",    studentAnswers != null ? studentAnswers : List.of());
            review.put("correctAnswer", correctAnswerList);
            review.put("questionBlocks", parseBlocks(qDoc.getString("question_text")));
            review.put("options",       buildOptions(qDoc));
            review.put("solutionBlocks", parseBlocks(qDoc.getString("explanation")));
            reviews.add(review);
        }
        return reviews;
    }

    /**
     * Calls exam-service's internal endpoint to get the ordered question IDs for this
     * student's paper, then fetches the full question documents from MongoDB.
     * Falls back to answered questions only if the call fails.
     */
    @SuppressWarnings("unchecked")
    private List<Document> fetchPaperQuestions(Result result) {
        UUID examId = result.getExamId();
        UUID studentId = result.getStudentId();
        try {
            String url = examServiceUrl + "/api/v1/exams/internal/" + examId + "/paper-ids?studentId=" + studentId;
            List<String> questionIds = restTemplate.getForObject(url, List.class);
            if (questionIds != null && !questionIds.isEmpty()) {
                // Convert string IDs to ObjectIds where possible
                List<Object> objectIds = questionIds.stream()
                        .map(id -> {
                            try { return new org.bson.types.ObjectId(id); }
                            catch (Exception e) { return id; }
                        })
                        .collect(java.util.stream.Collectors.toList());
                Query q = new Query(Criteria.where("_id").in(objectIds));
                List<Document> docs = mongoTemplate.find(q, Document.class, "questions");
                // Re-order docs to match the paper order
                Map<String, Document> byId = new java.util.HashMap<>();
                for (Document d : docs) {
                    String key = d.getObjectId("_id") != null
                            ? d.getObjectId("_id").toString()
                            : d.getString("_id");
                    byId.put(key, d);
                }
                List<Document> ordered = new ArrayList<>();
                for (String id : questionIds) {
                    Document d = byId.get(id);
                    if (d != null) ordered.add(d);
                }
                return ordered;
            }
        } catch (Exception e) {
            log.warn("Could not fetch paper question IDs from exam-service for exam {} student {}: {}",
                    examId, studentId, e.getMessage());
        }
        // Fallback: return only answered questions from snapshot
        return List.of();
    }

    private boolean evaluate(List<String> studentAnswers, String correctAnswer, String type) {
        if (studentAnswers == null || studentAnswers.isEmpty()) return false;
        if (correctAnswer == null || correctAnswer.isBlank()) return false;

        if ("NUMERICAL".equalsIgnoreCase(type)) {
            try {
                double sv = Double.parseDouble(studentAnswers.get(0).toString().trim());
                double cv = Double.parseDouble(correctAnswer.trim());
                return Math.abs(sv - cv) < 0.01;
            } catch (NumberFormatException e) { return false; }
        }
        List<String> studentSorted = studentAnswers.stream()
                .map(s -> s.toUpperCase().trim()).sorted().toList();
        List<String> correctSorted = Arrays.stream(correctAnswer.split("[,|]"))
                .map(s -> s.toUpperCase().trim()).filter(s -> !s.isEmpty()).sorted().toList();
        return studentSorted.equals(correctSorted);
    }

    /**
     * Parse a string containing $...$ (inline) and $$...$$ (display) LaTeX into
     * typed content blocks: TEXT, INLINE_MATH, MATH.
     * The frontend's renderBlocks() passes INLINE_MATH / MATH through KaTeX.
     */
    private List<Map<String, Object>> parseBlocks(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<Map<String, Object>> blocks = new ArrayList<>();
        int i = 0;
        int len = text.length();
        StringBuilder buf = new StringBuilder();
        while (i < len) {
            // Display math: $$...$$
            if (i + 1 < len && text.charAt(i) == '$' && text.charAt(i + 1) == '$') {
                if (!buf.isEmpty()) { blocks.add(Map.of("type", "TEXT", "value", buf.toString())); buf.setLength(0); }
                int end = text.indexOf("$$", i + 2);
                if (end == -1) { buf.append(text.substring(i)); break; }
                blocks.add(Map.of("type", "MATH", "value", text.substring(i + 2, end)));
                i = end + 2;
            // Inline math: $...$
            } else if (text.charAt(i) == '$') {
                if (!buf.isEmpty()) { blocks.add(Map.of("type", "TEXT", "value", buf.toString())); buf.setLength(0); }
                int end = text.indexOf('$', i + 1);
                if (end == -1) { buf.append(text.substring(i)); break; }
                blocks.add(Map.of("type", "INLINE_MATH", "value", text.substring(i + 1, end)));
                i = end + 1;
            } else {
                buf.append(text.charAt(i++));
            }
        }
        if (!buf.isEmpty()) blocks.add(Map.of("type", "TEXT", "value", buf.toString()));
        return blocks;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildOptions(Document qDoc) {
        if (qDoc == null) return List.of();
        List<Document> opts = (List<Document>) qDoc.get("options");
        if (opts == null) return List.of();
        return opts.stream().map(opt -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id",     opt.getString("id"));
            o.put("blocks", parseBlocks(opt.getString("text")));
            return o;
        }).toList();
    }

    private String fetchExamTitle(UUID examId) {
        try {
            String url = examServiceUrl + "/api/v1/exams/" + examId;
            Map<?, ?> exam = restTemplate.getForObject(url, Map.class);
            if (exam != null && exam.get("title") instanceof String t) return t;
        } catch (Exception e) {
            log.warn("Could not fetch exam title for {}: {}", examId, e.getMessage());
        }
        return "Exam";
    }

    private double getDouble(Document doc, String field, double def) {
        Object v = doc.get(field);
        if (v == null) return def;
        try { return ((Number) v).doubleValue(); } catch (ClassCastException e) { return def; }
    }
}
