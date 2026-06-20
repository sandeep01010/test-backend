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
import java.util.stream.Collectors;

/**
 * Computes rich per-attempt analysis for the "View Analysis" screen.
 *
 * Data sources:
 *   - PostgreSQL result record  (score, rank, percentile, sectionScores)
 *   - MongoDB answer_snapshots  (per-question answer + timeSpentSecs)
 *   - MongoDB questions          (subject, topic, marks, correct_answer)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final ResultRepository resultRepository;
    private final MongoTemplate mongoTemplate;
    private final RestTemplate restTemplate;

    @Value("${services.exam-service-url:http://exam-service:8082}")
    private String examServiceUrl;

    // ── Public entry point ────────────────────────────────────────────────────

    public Map<String, Object> getAnalysis(UUID examId, UUID studentId, UUID sessionId) {
        Result result = findResult(examId, studentId, sessionId);

        String resolvedSession = result.getSessionId() != null
                ? result.getSessionId().toString()
                : (sessionId != null ? sessionId.toString() : null);

        Document snapshot = resolvedSession != null ? fetchSnapshot(resolvedSession) : null;

        List<Map<String, Object>> questions = buildQuestionAnalysis(snapshot, result);

        // Subject max marks: sum of question.marks per subject (for pct calculations)
        Map<String, Double> subjectMaxMarks = computeSubjectMaxMarks(questions);

        List<Map<String, Object>> subjectPerf =
                computeSubjectPerformance(questions, subjectMaxMarks, result, examId);

        List<Map<String, Object>> trend = computeScoreTrend(examId, studentId, subjectMaxMarks);

        Map<String, Object> behavior = computeAnswerBehavior(questions);

        List<Map<String, Object>> insights = generateInsights(questions, behavior);

        // Totals
        double totalScore  = result.getTotalScore()  != null ? result.getTotalScore()  : 0;
        double totalMarks  = result.getTotalMarks()  != null && result.getTotalMarks() > 0
                ? result.getTotalMarks()
                : subjectMaxMarks.values().stream().mapToDouble(Double::doubleValue).sum();
        int    attempted   = getInt(behavior, "attempted");
        boolean hasRealTime = questions.stream().anyMatch(q -> getLong(q, "timeSpentSecs") > 0);
        long   totalTimeSecs = questions.stream().mapToLong(q -> getLong(q, "timeSpentSecs")).sum();
        long   avgTimePerQ = attempted > 0 ? totalTimeSecs / attempted : 0;
        double accuracy    = attempted > 0
                ? Math.round(getInt(behavior, "correct") * 100.0 / attempted * 10) / 10.0 : 0;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("examTitle",        fetchExamTitle(examId));
        resp.put("attemptNumber",    result.getAttemptNumber() != null ? result.getAttemptNumber() : 1);
        resp.put("submittedAt",      result.getSubmittedAt()  != null ? result.getSubmittedAt().toString() : "");
        resp.put("score",            totalScore);
        resp.put("totalMarks",       totalMarks);
        resp.put("accuracy",         accuracy);
        resp.put("rank",             result.getRank()       != null ? result.getRank()       : 0);
        resp.put("percentile",       result.getPercentile() != null ? result.getPercentile() : 0.0);
        resp.put("totalTimeSecs",    totalTimeSecs);
        resp.put("avgTimePerQSecs",  avgTimePerQ);
        resp.put("subjectPerformance", subjectPerf);
        resp.put("scoreTrend",         trend);
        resp.put("hasRealTimeData",    hasRealTime);
        resp.put("questionHeatmap",    buildHeatmap(questions));
        resp.put("answerBehavior",     behavior);
        resp.put("insights",           insights);
        resp.put("sectionScores",       result.getSectionScores() != null ? result.getSectionScores() : Map.of());
        resp.put("subjectMaxMarks",     subjectMaxMarks);
        resp.put("speedAccuracyMatrix", computeSpeedAccuracyMatrix(questions));
        resp.put("difficultyBreakdown", computeDifficultyBreakdown(questions));
        resp.put("topicBreakdown",      computeTopicBreakdown(questions));
        resp.put("tagBreakdown",        computeTagBreakdown(questions));
        resp.put("peerSubjectPerf",     computePeerSubjectPerf(examId, subjectPerf, subjectMaxMarks));
        return resp;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Result findResult(UUID examId, UUID studentId, UUID sessionId) {
        if (sessionId != null) {
            return resultRepository.findBySessionId(sessionId)
                    .orElseThrow(() -> new RuntimeException("Result not ready"));
        }
        return resultRepository
                .findByStudentIdAndExamIdOrderByAttemptNumberAsc(studentId, examId)
                .stream()
                .max(Comparator.comparingInt(r -> r.getAttemptNumber() != null ? r.getAttemptNumber() : 0))
                .orElseThrow(() -> new RuntimeException("Result not found"));
    }

    private Document fetchSnapshot(String sessionId) {
        return mongoTemplate.findOne(
                new Query(Criteria.where("_id").is(sessionId)),
                Document.class, "answer_snapshots");
    }

    @SuppressWarnings("unchecked")
    private List<Document> fetchPaperQuestions(Result result) {
        UUID examId = result.getExamId();
        UUID studentId = result.getStudentId();
        try {
            String url = examServiceUrl + "/api/v1/exams/internal/" + examId + "/paper-ids?studentId=" + studentId;
            List<String> questionIds = restTemplate.getForObject(url, List.class);
            if (questionIds != null && !questionIds.isEmpty()) {
                List<Object> objectIds = questionIds.stream()
                        .map(id -> { try { return (Object) new org.bson.types.ObjectId(id); } catch (Exception e) { return (Object) id; } })
                        .collect(Collectors.toList());
                List<Document> docs = mongoTemplate.find(
                        new Query(Criteria.where("_id").in(objectIds)), Document.class, "questions");
                Map<String, Document> byId = new HashMap<>();
                for (Document d : docs) {
                    String key = d.getObjectId("_id") != null ? d.getObjectId("_id").toString() : d.getString("_id");
                    byId.put(key, d);
                }
                List<Document> ordered = new ArrayList<>();
                for (String id : questionIds) { Document d = byId.get(id); if (d != null) ordered.add(d); }
                return ordered;
            }
        } catch (Exception e) {
            log.warn("Could not fetch paper question IDs for exam {} student {}: {}", examId, studentId, e.getMessage());
        }
        return List.of();
    }

    private List<Map<String, Object>> buildQuestionAnalysis(Document snapshot, Result result) {
        Document answersDoc = snapshot != null ? snapshot.get("answers", Document.class) : null;

        // Get all questions for this paper (all 90, ordered)
        List<Document> paperDocs = fetchPaperQuestions(result);

        // Fallback: if paper fetch failed, use only answered questions from snapshot
        if (paperDocs.isEmpty() && answersDoc != null) {
            Set<String> qIds = answersDoc.keySet();
            paperDocs = new ArrayList<>(mongoTemplate.find(
                    new Query(Criteria.where("_id").in(qIds)), Document.class, "questions"));
        }

        List<Map<String, Object>> resultList = new ArrayList<>();
        int order = 1;
        for (Document qDoc : paperDocs) {
            String qId = qDoc.getObjectId("_id") != null
                    ? qDoc.getObjectId("_id").toString() : qDoc.getString("_id");
            Document ansEntry = answersDoc != null ? answersDoc.get(qId, Document.class) : null;

            List<String> studentAns = ansEntry != null
                    ? ansEntry.getList("answer", String.class) : List.of();
            boolean markedForReview = ansEntry != null
                    && Boolean.TRUE.equals(ansEntry.getBoolean("markedForReview"));
            boolean wasChanged = ansEntry != null
                    && Boolean.TRUE.equals(ansEntry.getBoolean("wasChanged"));
            long timeSpent = ansEntry != null ? docLong(ansEntry, "timeSpentSecs") : 0;

            boolean skipped = studentAns == null || studentAns.isEmpty();
            String  type    = qDoc.getString("type") != null ? qDoc.getString("type") : "MCQ";
            double  marks   = docDouble(qDoc, "marks", 4.0);
            String  subject = qDoc.getString("subject") != null ? qDoc.getString("subject") : "GENERAL";
            String  chapter = qDoc.getString("chapter");
            String  topic   = qDoc.getString("topic");
            String  correctAnswer = qDoc.getString("correct_answer");

            String status = skipped ? "SKIPPED"
                    : (evaluate(studentAns, correctAnswer, type) ? "CORRECT" : "WRONG");

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("questionNo",      order++);
            entry.put("questionId",      qId);
            entry.put("subject",         subject);
            entry.put("chapter",         chapter);
            entry.put("topic",           topic);
            entry.put("status",          status);
            entry.put("marks",           marks);
            entry.put("timeSpentSecs",   timeSpent);
            entry.put("markedForReview", markedForReview);
            entry.put("wasChanged",      wasChanged);
            String difficulty = qDoc.getString("difficulty");
            @SuppressWarnings("unchecked")
            List<String> tags = qDoc.getList("tags", String.class);
            entry.put("difficulty", difficulty != null ? difficulty : "MEDIUM");
            entry.put("tags",       tags != null ? tags : List.of());
            resultList.add(entry);
        }
        return resultList;
    }

    private Map<String, Double> computeSubjectMaxMarks(List<Map<String, Object>> questions) {
        Map<String, Double> max = new LinkedHashMap<>();
        for (Map<String, Object> q : questions) {
            String subject = (String) q.getOrDefault("subject", "GENERAL");
            double marks   = getDouble(q, "marks");
            max.merge(subject, marks, Double::sum);
        }
        return max;
    }

    private List<Map<String, Object>> computeSubjectPerformance(
            List<Map<String, Object>> questions,
            Map<String, Double> subjectMaxMarks,
            Result studentResult,
            UUID examId) {

        // Student's earned marks per subject from sectionScores
        Map<String, Double> studentSection =
                studentResult.getSectionScores() != null ? studentResult.getSectionScores() : Map.of();

        // Top ranker's sectionScores (rank = 1 student)
        Map<String, Double> topSection = findTopRankerSectionScores(examId);

        // Also compute accuracy from per-question data for student
        Map<String, long[]> subjectCounts = new LinkedHashMap<>(); // [correct, total]
        for (Map<String, Object> q : questions) {
            String sub = (String) q.getOrDefault("subject", "GENERAL");
            subjectCounts.computeIfAbsent(sub, k -> new long[]{0, 0});
            subjectCounts.get(sub)[1]++;
            if ("CORRECT".equals(q.get("status"))) subjectCounts.get(sub)[0]++;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String subject : subjectCounts.keySet()) {
            long[] counts  = subjectCounts.get(subject);
            double maxM    = subjectMaxMarks.getOrDefault(subject, 1.0);

            // Student accuracy from per-question data
            double yourPct  = counts[1] > 0
                    ? Math.round(counts[0] * 100.0 / counts[1] * 10) / 10.0 : 0;

            // Top ranker accuracy: sectionScore / subjectMaxMarks * 100
            double topScore = topSection.getOrDefault(subject, 0.0);
            double topPct   = maxM > 0 ? Math.round(topScore / maxM * 100.0 * 10) / 10.0 : 0;
            topPct = Math.min(100, topPct);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("subject",       subject);
            m.put("yourCorrect",   counts[0]);
            m.put("yourTotal",     counts[1]);
            m.put("yourPct",       yourPct);
            m.put("topRankerPct",  topPct);
            result.add(m);
        }
        return result;
    }

    private Map<String, Double> findTopRankerSectionScores(UUID examId) {
        try {
            List<Result> ranked = resultRepository.findByExamIdOrderByRankAsc(examId);
            Result top = ranked.stream()
                    .filter(r -> r.getRank() != null && r.getRank() == 1)
                    .findFirst()
                    .orElse(ranked.isEmpty() ? null : ranked.get(0));
            if (top == null || top.getSectionScores() == null) return Map.of();
            return top.getSectionScores();
        } catch (Exception e) {
            log.warn("Could not find top ranker for exam {}: {}", examId, e.getMessage());
            return Map.of();
        }
    }

    private List<Map<String, Object>> computeScoreTrend(UUID examId, UUID studentId, Map<String, Double> subjectMaxMarks) {
        return resultRepository
                .findByStudentIdAndExamIdOrderByAttemptNumberAsc(studentId, examId)
                .stream()
                .filter(r -> r.getTotalScore() != null)
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("attemptNo",  r.getAttemptNumber() != null ? r.getAttemptNumber() : 1);
                    m.put("score",      r.getTotalScore());
                    m.put("totalMarks", r.getTotalMarks() != null ? r.getTotalMarks() : 0);
                    if (r.getSectionScores() != null && !subjectMaxMarks.isEmpty()) {
                        Map<String, Double> subAcc = new LinkedHashMap<>();
                        for (Map.Entry<String, Double> e : r.getSectionScores().entrySet()) {
                            double maxM = subjectMaxMarks.getOrDefault(e.getKey(), 1.0);
                            double pct = maxM > 0 ? Math.round(e.getValue() / maxM * 100 * 10) / 10.0 : 0;
                            subAcc.put(e.getKey(), Math.min(100, Math.max(0, pct)));
                        }
                        m.put("subjectAccuracy", subAcc);
                    }
                    return m;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildHeatmap(List<Map<String, Object>> questions) {
        boolean hasRealTime = questions.stream().anyMatch(q -> getLong(q, "timeSpentSecs") > 0);

        return questions.stream().map(q -> {
            long time = getLong(q, "timeSpentSecs");
            String status = (String) q.get("status");

            // Skipped questions always get their own distinct bucket regardless of timing
            String bucket;
            if ("SKIPPED".equals(status)) {
                bucket = "SKIPPED";
            } else if (!hasRealTime) {
                time = switch (status) {
                    case "CORRECT" -> 30;
                    case "WRONG"   -> Boolean.TRUE.equals(q.get("markedForReview")) ? 130 : 90;
                    default        -> 30;
                };
                bucket = time <= 60 ? "NORMAL" : time <= 120 ? "CAREFUL" : "STRUGGLING";
            } else {
                if      (time < 15)   bucket = "GUESSING";
                else if (time <= 60)  bucket = "NORMAL";
                else if (time <= 120) bucket = "CAREFUL";
                else                  bucket = "STRUGGLING";
            }

            Map<String, Object> h = new LinkedHashMap<>();
            h.put("questionNo",    q.get("questionNo"));
            h.put("subject",       q.get("subject"));
            h.put("chapter",       q.get("chapter"));
            h.put("topic",         q.get("topic"));
            h.put("status",        status);
            h.put("timeSpentSecs", time);
            h.put("timeBucket",    bucket);
            h.put("estimated",     !hasRealTime);
            h.put("difficulty",    q.get("difficulty"));
            h.put("tags",          q.get("tags"));
            return h;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> computeAnswerBehavior(List<Map<String, Object>> questions) {
        int total    = questions.size();
        int correct  = (int) questions.stream().filter(q -> "CORRECT".equals(q.get("status"))).count();
        int wrong    = (int) questions.stream().filter(q -> "WRONG".equals(q.get("status"))).count();
        int skipped  = (int) questions.stream().filter(q -> "SKIPPED".equals(q.get("status"))).count();
        int attempted = correct + wrong;

        int markedForReview  = (int) questions.stream()
                .filter(q -> Boolean.TRUE.equals(q.get("markedForReview"))).count();
        // wasChanged tracks actual answer switches (non-empty → different non-empty answer)
        int changedToCorrect = (int) questions.stream()
                .filter(q -> Boolean.TRUE.equals(q.get("wasChanged")) && "CORRECT".equals(q.get("status"))).count();
        int changedToWrong   = (int) questions.stream()
                .filter(q -> Boolean.TRUE.equals(q.get("wasChanged")) && "WRONG".equals(q.get("status"))).count();

        boolean hasRealTime = questions.stream().anyMatch(q -> getLong(q, "timeSpentSecs") > 0);

        // Speed-guessed: <15s AND wrong/skipped (meaningful only when time data exists)
        int speedGuessed = hasRealTime
                ? (int) questions.stream()
                    .filter(q -> getLong(q, "timeSpentSecs") > 0
                              && getLong(q, "timeSpentSecs") < 15
                              && !"CORRECT".equals(q.get("status")))
                    .count()
                : 0;

        // Time wasted: >120s AND wrong
        long timeWastedSecs = hasRealTime
                ? questions.stream()
                    .filter(q -> getLong(q, "timeSpentSecs") > 120 && "WRONG".equals(q.get("status")))
                    .mapToLong(q -> getLong(q, "timeSpentSecs")).sum()
                : 0;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total",            total);
        m.put("attempted",        attempted);
        m.put("correct",          correct);
        m.put("wrong",            wrong);
        m.put("skipped",          skipped);
        m.put("markedForReview",  markedForReview);
        m.put("changedToCorrect", changedToCorrect);
        m.put("changedToWrong",   changedToWrong);
        m.put("speedGuessed",     speedGuessed);
        m.put("timeWastedSecs",   timeWastedSecs);
        return m;
    }

    private List<Map<String, Object>> generateInsights(
            List<Map<String, Object>> questions,
            Map<String, Object> behavior) {

        List<Map<String, Object>> insights = new ArrayList<>();

        // Time Drain
        int wrongAfter120 = (int) questions.stream()
                .filter(q -> getLong(q, "timeSpentSecs") > 120 && "WRONG".equals(q.get("status"))).count();
        long wastedSecs   = getLong(behavior, "timeWastedSecs");
        long wastedMins   = wastedSecs / 60;
        if (wrongAfter120 > 0 || wastedSecs > 0) {
            insights.add(insight("TIME_DRAIN", "Time Drain",
                    String.format("%d+ minutes spent on %d questions answered wrong after >120s each. " +
                            "Redistributing this time could cover %d–%d more solvable questions.",
                            Math.max(1, wastedMins), wrongAfter120,
                            Math.max(1, wrongAfter120 / 2), Math.max(2, wrongAfter120))));
        }

        // Answer Changes
        int changed = getInt(behavior, "changedToCorrect") + getInt(behavior, "changedToWrong");
        if (changed > 0) {
            int c2c  = getInt(behavior, "changedToCorrect");
            int c2w  = getInt(behavior, "changedToWrong");
            // Assuming 4 marks per correct, 1 negative per wrong
            int recovered = c2c * 4;
            int lost      = c2w;
            int net       = recovered - lost;
            String sign   = net >= 0 ? "+" : "";
            insights.add(insight("ANSWER_CHANGES", "Answer Changes",
                    String.format("%d answers changed — %d improved the result (+%d marks recovered), " +
                            "%d hurt it (−%d marks lost). Net impact: %s%d marks from re-attempts.",
                            changed, c2c, recovered, c2w, lost, sign, net)));
        } else if (getInt(behavior, "markedForReview") > 0) {
            int rev = getInt(behavior, "markedForReview");
            insights.add(insight("ANSWER_CHANGES", "Marked for Review",
                    String.format("%d questions were flagged for review. " +
                            "Revisiting flagged questions can help recover marks if done within time budget.",
                            rev)));
        }

        // Speed Pattern
        int speedGuessed = getInt(behavior, "speedGuessed");
        if (speedGuessed > 0) {
            insights.add(insight("SPEED_PATTERN", "Speed Pattern",
                    String.format("%d questions answered in under 15 seconds with near-zero accuracy. " +
                            "A skip-and-return strategy would help recover these marks.", speedGuessed)));
        }

        // Fatigue Drop (first-half vs second-half accuracy)
        int half = questions.size() / 2;
        if (half > 3) {
            List<Map<String, Object>> first  = questions.subList(0, half);
            List<Map<String, Object>> second = questions.subList(half, questions.size());
            long firstAttempted  = first.stream().filter(q -> !"SKIPPED".equals(q.get("status"))).count();
            long secondAttempted = second.stream().filter(q -> !"SKIPPED".equals(q.get("status"))).count();
            long firstCorrect    = first.stream().filter(q -> "CORRECT".equals(q.get("status"))).count();
            long secondCorrect   = second.stream().filter(q -> "CORRECT".equals(q.get("status"))).count();

            if (firstAttempted > 0 && secondAttempted > 0) {
                double firstAcc  = firstCorrect  * 100.0 / firstAttempted;
                double secondAcc = secondCorrect * 100.0 / secondAttempted;
                if (firstAcc > secondAcc + 8) {
                    insights.add(insight("FATIGUE_DROP", "Fatigue Drop",
                            String.format("First-half accuracy: %.0f%%. Second-half accuracy: %.0f%%. " +
                                    "Accuracy consistently fell after the mid-exam mark. " +
                                    "Pacing and mental endurance are key focus areas.",
                                    firstAcc, secondAcc)));
                }
            }
        }

        return insights;
    }

    private Map<String, Object> insight(String key, String title, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key",     key);
        m.put("title",   title);
        m.put("message", message);
        return m;
    }

    private boolean evaluate(List<String> studentAns, String correctAnswer, String type) {
        if (studentAns == null || studentAns.isEmpty() || correctAnswer == null) return false;
        if ("NUMERICAL".equalsIgnoreCase(type)) {
            try {
                return Math.abs(
                        Double.parseDouble(studentAns.get(0).trim()) -
                        Double.parseDouble(correctAnswer.trim())) < 0.01;
            } catch (NumberFormatException e) { return false; }
        }
        List<String> ss = studentAns.stream().map(s -> s.toUpperCase().trim()).sorted().toList();
        List<String> cs = Arrays.stream(correctAnswer.split("[,|]"))
                .map(s -> s.toUpperCase().trim()).filter(s -> !s.isEmpty()).sorted().toList();
        return ss.equals(cs);
    }

    private String fetchExamTitle(UUID examId) {
        try {
            Map<?, ?> exam = restTemplate.getForObject(
                    examServiceUrl + "/api/v1/exams/" + examId, Map.class);
            if (exam != null && exam.get("title") instanceof String t) return t;
        } catch (Exception e) {
            log.warn("Could not fetch exam title for {}: {}", examId, e.getMessage());
        }
        return "Exam";
    }

    // ── New analysis methods ──────────────────────────────────────────────────

    private Map<String, Object> computeSpeedAccuracyMatrix(List<Map<String, Object>> questions) {
        boolean hasRealTime = questions.stream().anyMatch(q -> getLong(q, "timeSpentSecs") > 0);
        long threshold = 60;
        long fastCorrect = 0, fastWrong = 0, slowCorrect = 0, slowWrong = 0, slowWrongTimeSecs = 0;
        for (Map<String, Object> q : questions) {
            String status = (String) q.get("status");
            if ("SKIPPED".equals(status)) continue;
            long t = getLong(q, "timeSpentSecs");
            boolean fast = !hasRealTime || t < threshold;
            if ("CORRECT".equals(status)) {
                if (fast) fastCorrect++; else slowCorrect++;
            } else {
                if (fast) fastWrong++; else { slowWrong++; slowWrongTimeSecs += t; }
            }
        }
        long skipped = questions.stream().filter(q -> "SKIPPED".equals(q.get("status"))).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fastCorrect",       fastCorrect);
        m.put("fastWrong",         fastWrong);
        m.put("slowCorrect",       slowCorrect);
        m.put("slowWrong",         slowWrong);
        m.put("slowWrongTimeSecs", slowWrongTimeSecs);
        m.put("skipped",           skipped);
        return m;
    }

    private List<Map<String, Object>> computeDifficultyBreakdown(List<Map<String, Object>> questions) {
        String[] order = {"EASY", "MEDIUM", "HARD", "VERY_HARD"};
        Map<String, long[]> byDiff = new LinkedHashMap<>();
        for (String d : order) byDiff.put(d, new long[3]);
        for (Map<String, Object> q : questions) {
            String diff = (String) q.getOrDefault("difficulty", "MEDIUM");
            if (diff == null || diff.isBlank()) diff = "MEDIUM";
            byDiff.computeIfAbsent(diff, k -> new long[3]);
            String status = (String) q.get("status");
            if ("CORRECT".equals(status))      byDiff.get(diff)[0]++;
            else if ("WRONG".equals(status))   byDiff.get(diff)[1]++;
            else                               byDiff.get(diff)[2]++;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : order) {
            long[] c = byDiff.getOrDefault(key, new long[3]);
            long total = c[0] + c[1] + c[2];
            if (total == 0) continue;
            long attempted = c[0] + c[1];
            double pct = attempted > 0 ? Math.round(c[0] * 100.0 / attempted * 10) / 10.0 : 0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("difficulty", key);
            m.put("total",   total);
            m.put("correct", c[0]);
            m.put("wrong",   c[1]);
            m.put("skipped", c[2]);
            m.put("yourPct", pct);
            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> computeTopicBreakdown(List<Map<String, Object>> questions) {
        Map<String, long[]> byTopic = new LinkedHashMap<>();
        Map<String, String> topicSubject = new LinkedHashMap<>();
        for (Map<String, Object> q : questions) {
            String topic   = (String) q.get("topic");
            String chapter = (String) q.get("chapter");
            String subject = (String) q.getOrDefault("subject", "GENERAL");
            String key     = topic != null ? topic : (chapter != null ? chapter : subject);
            byTopic.computeIfAbsent(key, k -> new long[3]);
            topicSubject.putIfAbsent(key, subject);
            String status = (String) q.get("status");
            if ("CORRECT".equals(status))      byTopic.get(key)[0]++;
            else if ("WRONG".equals(status))   byTopic.get(key)[1]++;
            else                               byTopic.get(key)[2]++;
        }
        return byTopic.entrySet().stream()
            .map(e -> {
                long[] c = e.getValue();
                long attempted = c[0] + c[1];
                double pct = attempted > 0 ? Math.round(c[0] * 100.0 / attempted * 10) / 10.0 : 0;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("topic",   e.getKey());
                m.put("subject", topicSubject.get(e.getKey()));
                m.put("total",   c[0] + c[1] + c[2]);
                m.put("correct", c[0]);
                m.put("wrong",   c[1]);
                m.put("skipped", c[2]);
                m.put("yourPct", pct);
                return m;
            })
            .sorted(Comparator.comparingDouble(m -> getDouble(m, "yourPct")))
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> computeTagBreakdown(List<Map<String, Object>> questions) {
        Map<String, long[]> byTag = new LinkedHashMap<>();
        for (Map<String, Object> q : questions) {
            List<String> tags = (List<String>) q.getOrDefault("tags", List.of());
            if (tags == null || tags.isEmpty()) continue;
            String status = (String) q.get("status");
            for (String tag : tags) {
                if (tag == null || tag.isBlank()) continue;
                byTag.computeIfAbsent(tag, k -> new long[3]);
                if ("CORRECT".equals(status))      byTag.get(tag)[0]++;
                else if ("WRONG".equals(status))   byTag.get(tag)[1]++;
                else                               byTag.get(tag)[2]++;
            }
        }
        return byTag.entrySet().stream()
            .filter(e -> e.getValue()[0] + e.getValue()[1] > 0)
            .map(e -> {
                long[] c = e.getValue();
                long attempted = c[0] + c[1];
                double pct = Math.round(c[0] * 100.0 / attempted * 10) / 10.0;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tag",     e.getKey());
                m.put("total",   c[0] + c[1] + c[2]);
                m.put("correct", c[0]);
                m.put("wrong",   c[1]);
                m.put("skipped", c[2]);
                m.put("yourPct", pct);
                return m;
            })
            .sorted(Comparator.comparingDouble(m -> getDouble(m, "yourPct")))
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> computePeerSubjectPerf(
            UUID examId,
            List<Map<String, Object>> subjectPerf,
            Map<String, Double> subjectMaxMarks) {
        try {
            List<Result> allResults = resultRepository.findByExamIdOrderByRankAsc(examId);
            if (allResults.size() < 2) return List.of();
            Map<String, double[]> acc = new LinkedHashMap<>();
            for (Result r : allResults) {
                if (r.getSectionScores() == null) continue;
                for (Map.Entry<String, Double> e : r.getSectionScores().entrySet()) {
                    acc.computeIfAbsent(e.getKey(), k -> new double[]{0, 0});
                    acc.get(e.getKey())[0] += e.getValue();
                    acc.get(e.getKey())[1]++;
                }
            }
            Map<String, Double> yourPctMap = subjectPerf.stream()
                .collect(Collectors.toMap(
                    m -> (String) m.get("subject"),
                    m -> getDouble(m, "yourPct")));
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<String, double[]> e : acc.entrySet()) {
                if (e.getValue()[1] == 0) continue;
                String subject  = e.getKey();
                double avgScore = e.getValue()[0] / e.getValue()[1];
                double maxM     = subjectMaxMarks.getOrDefault(subject, 1.0);
                double peerPct  = maxM > 0 ? Math.min(100, Math.round(avgScore / maxM * 100 * 10) / 10.0) : 0;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("subject",    subject);
                m.put("peerAvgPct", peerPct);
                m.put("yourPct",    yourPctMap.getOrDefault(subject, 0.0));
                result.add(m);
            }
            return result;
        } catch (Exception e) {
            log.warn("Could not compute peer subject perf for exam {}: {}", examId, e.getMessage());
            return List.of();
        }
    }

    // ── Type-safe accessors ───────────────────────────────────────────────────

    private double docDouble(Document d, String field, double def) {
        Object v = d.get(field);
        if (v == null) return def;
        try { return ((Number) v).doubleValue(); } catch (ClassCastException e) { return def; }
    }

    private long docLong(Document d, String field) {
        Object v = d.get(field);
        if (v == null) return 0;
        try { return ((Number) v).longValue(); } catch (ClassCastException e) { return 0; }
    }

    private long getLong(Map<String, Object> m, String f) {
        Object v = m.get(f);
        return v instanceof Number n ? n.longValue() : 0;
    }

    private int getInt(Map<String, Object> m, String f) {
        Object v = m.get(f);
        return v instanceof Number n ? n.intValue() : 0;
    }

    private double getDouble(Map<String, Object> m, String f) {
        Object v = m.get(f);
        return v instanceof Number n ? n.doubleValue() : 0;
    }
}
