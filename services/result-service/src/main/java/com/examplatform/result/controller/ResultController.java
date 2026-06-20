package com.examplatform.result.controller;

import com.examplatform.result.service.AnalysisService;
import com.examplatform.result.service.DetailedResultService;
import com.examplatform.result.service.EvaluationService;
import com.examplatform.result.service.ResultQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/results")
@RequiredArgsConstructor
public class ResultController {

    private final ResultQueryService queryService;
    private final EvaluationService evaluationService;
    private final DetailedResultService detailedResultService;
    private final AnalysisService analysisService;

    /** Rich per-attempt analysis (Overview + Deep Analysis screens). */
    @GetMapping("/{examId}/analysis")
    public ResponseEntity<Map<String, Object>> getAnalysis(
            @PathVariable UUID examId,
            @RequestHeader("X-User-Id") UUID studentId,
            @RequestParam(required = false) UUID session) {
        return ResponseEntity.ok(analysisService.getAnalysis(examId, studentId, session));
    }

    /** Per-question detailed result for review. */
    @GetMapping("/{examId}/detailed")
    public ResponseEntity<Map<String, Object>> getDetailedResult(
            @PathVariable UUID examId,
            @RequestHeader("X-User-Id") UUID studentId,
            @RequestParam(required = false) UUID session) {
        return ResponseEntity.ok(detailedResultService.getDetailedResult(examId, studentId, session));
    }

    /** All attempts for the calling student on a given exam. */
    @GetMapping("/exam/{examId}/attempts")
    public ResponseEntity<List<Map<String, Object>>> getMyAttempts(
            @PathVariable UUID examId,
            @RequestHeader("X-User-Id") UUID studentId) {
        return ResponseEntity.ok(queryService.getStudentAttempts(examId, studentId));
    }

    @GetMapping("/{examId}/{studentId}")
    public ResponseEntity<Map<String, Object>> getResult(
            @PathVariable UUID examId,
            @PathVariable UUID studentId) {
        return ResponseEntity.ok(queryService.getStudentResult(examId, studentId));
    }

    @GetMapping("/{examId}/leaderboard")
    public ResponseEntity<Map<String, Object>> getLeaderboard(
            @PathVariable UUID examId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(queryService.getLeaderboard(examId, limit));
    }

    @GetMapping("/{examId}/analytics")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getAnalytics(@PathVariable UUID examId) {
        return ResponseEntity.ok(queryService.getExamAnalytics(examId));
    }

    @PostMapping("/{examId}/compute")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> computeRanks(@PathVariable UUID examId) {
        evaluationService.computeRanks(examId);
        return ResponseEntity.ok(Map.of("message", "Rank computation triggered for exam " + examId));
    }

    @GetMapping("/{examId}/export")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportResults(@PathVariable UUID examId) {
        byte[] csv = queryService.exportResultsCsv(examId);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=results_" + examId + ".csv")
                .body(csv);
    }
}
