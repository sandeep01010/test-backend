package com.examplatform.result.service;

import com.examplatform.result.model.Result;
import com.examplatform.result.repository.ResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ResultQueryService {

    private final ResultRepository resultRepository;
    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getStudentAttempts(UUID examId, UUID studentId) {
        return resultRepository.findByStudentIdAndExamIdOrderByAttemptNumberAsc(studentId, examId)
                .stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("attemptNumber", r.getAttemptNumber() != null ? r.getAttemptNumber() : 1);
                    m.put("sessionId",     r.getSessionId() != null ? r.getSessionId().toString() : r.getId().toString());
                    m.put("examId",        r.getExamId().toString());
                    m.put("score",         r.getTotalScore() != null ? r.getTotalScore() : 0);
                    m.put("totalMarks",    r.getTotalMarks() != null ? r.getTotalMarks() : 0);
                    double pct = (r.getTotalMarks() != null && r.getTotalMarks() > 0)
                            ? (r.getTotalScore() / r.getTotalMarks()) * 100 : 0;
                    m.put("percentage",    Math.round(pct * 10.0) / 10.0);
                    m.put("rank",          r.getRank());
                    m.put("correctCount",  r.getCorrectCount());
                    m.put("wrongCount",    r.getWrongCount());
                    m.put("skippedCount",  r.getSkippedCount());
                    m.put("status",        r.getStatus() != null ? r.getStatus().name() : "EVALUATED");
                    m.put("submittedAt",   r.getSubmittedAt() != null
                            ? r.getSubmittedAt().toString()
                            : (r.getEvaluatedAt() != null ? r.getEvaluatedAt().toString() : r.getCreatedAt().toString()));
                    return m;
                })
                .toList();
    }

    public Map<String, Object> getStudentResult(UUID examId, UUID studentId) {
        return resultRepository.findByExamIdAndStudentId(examId, studentId)
                .map(r -> {
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("studentId", r.getStudentId());
                    resp.put("examId", r.getExamId());
                    resp.put("totalScore", r.getTotalScore());
                    resp.put("sectionScores", r.getSectionScores());
                    resp.put("correctCount", r.getCorrectCount());
                    resp.put("wrongCount", r.getWrongCount());
                    resp.put("skippedCount", r.getSkippedCount());
                    resp.put("rank", r.getRank());
                    resp.put("percentile", r.getPercentile());
                    resp.put("status", r.getStatus());
                    return resp;
                })
                .orElseThrow(() -> new RuntimeException("Result not found"));
    }

    public Map<String, Object> getLeaderboard(UUID examId, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT u.first_name || ' ' || u.last_name AS name,
                   r.total_score, r.rank, r.percentile,
                   r.correct_count, r.wrong_count
            FROM results r
            JOIN users u ON u.id = r.student_id
            WHERE r.exam_id = ? AND r.status = 'PUBLISHED'
            ORDER BY r.rank ASC
            LIMIT ?
            """, examId, limit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examId", examId);
        result.put("toppers", rows);
        result.put("totalParticipants", getTotalParticipants(examId));
        return result;
    }

    public Map<String, Object> getExamAnalytics(UUID examId) {
        Map<String, Object> stats = jdbcTemplate.queryForMap("""
            SELECT
                COUNT(*) AS total_appeared,
                AVG(total_score) AS avg_score,
                MAX(total_score) AS max_score,
                MIN(total_score) AS min_score,
                PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY total_score) AS median_score,
                AVG(correct_count) AS avg_correct,
                AVG(wrong_count) AS avg_wrong,
                AVG(skipped_count) AS avg_skipped
            FROM results
            WHERE exam_id = ? AND status IN ('EVALUATED','PUBLISHED')
            """, examId);

        // Score distribution (histogram)
        List<Map<String, Object>> distribution = jdbcTemplate.queryForList("""
            SELECT
                FLOOR(total_score / 10) * 10 AS score_range_start,
                COUNT(*) AS count
            FROM results
            WHERE exam_id = ? AND status IN ('EVALUATED','PUBLISHED')
            GROUP BY score_range_start
            ORDER BY score_range_start
            """, examId);

        Map<String, Object> analytics = new LinkedHashMap<>(stats);
        analytics.put("scoreDistribution", distribution);
        analytics.put("examId", examId);
        return analytics;
    }

    public byte[] exportResultsCsv(UUID examId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT r.rank, e.roll_number,
                   u.first_name || ' ' || u.last_name AS name,
                   u.email, r.total_score, r.correct_count,
                   r.wrong_count, r.skipped_count, r.percentile
            FROM results r
            JOIN enrollments e ON e.id = r.enrollment_id
            JOIN users u ON u.id = r.student_id
            WHERE r.exam_id = ? AND r.status = 'PUBLISHED'
            ORDER BY r.rank ASC
            """, examId);

        StringBuilder csv = new StringBuilder();
        csv.append("Rank,Roll Number,Name,Email,Score,Correct,Wrong,Skipped,Percentile\n");
        for (Map<String, Object> row : rows) {
            csv.append(row.get("rank")).append(",")
               .append(row.get("roll_number")).append(",")
               .append(row.get("name")).append(",")
               .append(row.get("email")).append(",")
               .append(row.get("total_score")).append(",")
               .append(row.get("correct_count")).append(",")
               .append(row.get("wrong_count")).append(",")
               .append(row.get("skipped_count")).append(",")
               .append(row.get("percentile")).append("\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private long getTotalParticipants(UUID examId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM results WHERE exam_id = ? AND status IN ('EVALUATED','PUBLISHED')",
                Long.class, examId);
        return count != null ? count : 0;
    }
}
