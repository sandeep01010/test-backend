package com.examplatform.testengine.controller;

import com.examplatform.testengine.dto.*;
import com.examplatform.testengine.model.SessionState;
import com.examplatform.testengine.service.SessionStateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionStateService sessionService;

    /**
     * Live platform metrics for the super-admin dashboard.
     * Admin-only: the API Gateway verifies the JWT and forwards the role as
     * the trusted X-User-Role header.
     */
    @GetMapping("/metrics/live")
    public ResponseEntity<Map<String, Object>> liveMetrics(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (role == null || !role.toUpperCase().contains("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(sessionService.getLiveMetrics());
    }

    /**
     * Start or resume an exam session.
     * If session already exists and is ACTIVE, returns existing state (resume).
     */
    @PostMapping("/start")
    public ResponseEntity<SessionStartResponse> startSession(
            @Valid @RequestBody StartSessionRequest req,
            @RequestAttribute("userId") UUID studentId) {

        String sessionId = UUID.randomUUID().toString();

        // Check for existing active session (resume scenario)
        // In production: look up by enrollmentId
        SessionState state = sessionService.createSession(
                sessionId,
                studentId.toString(),
                req.getExamId().toString(),
                req.getEnrollmentId().toString(),
                req.getDurationSeconds()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                SessionStartResponse.builder()
                        .sessionId(sessionId)
                        .status(state.getStatus())
                        .timeRemainingSecs(state.getTimeRemainingSecs())
                        .startedAt(state.getStartedAt())
                        .build()
        );
    }

    /**
     * Get current session state — used for reconnect/resume.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionStateResponse> getSession(
            @PathVariable String sessionId,
            @RequestAttribute("userId") UUID studentId) {

        SessionState state = sessionService.getSession(sessionId).orElse(null);
        if (state == null) {
            return ResponseEntity.<SessionStateResponse>notFound().build();
        }
        if (!state.getStudentId().equals(studentId.toString())) {
            return ResponseEntity.<SessionStateResponse>status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(SessionStateResponse.from(state));
    }

    /**
     * Save a single answer (HTTP fallback if WebSocket unavailable).
     */
    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<Map<String, Object>> saveAnswer(
            @PathVariable String sessionId,
            @Valid @RequestBody SaveAnswerRequest req,
            @RequestAttribute("userId") UUID studentId) {

        sessionService.saveAnswer(
                sessionId,
                req.getQuestionId(),
                req.getAnswer(),
                req.isMarkedForReview(),
                req.getTimeSpentSecs()
        );

        return ResponseEntity.ok(Map.of(
                "status", "SAVED",
                "questionId", req.getQuestionId(),
                "savedAt", Instant.now().toString()
        ));
    }

    /**
     * Final submission.
     */
    @PostMapping("/{sessionId}/submit")
    public ResponseEntity<Map<String, Object>> submitExam(
            @PathVariable String sessionId,
            @RequestParam String examId,
            @RequestAttribute("userId") UUID studentId) {

        sessionService.submitSession(sessionId, examId);

        return ResponseEntity.ok(Map.of(
                "status", "SUBMITTED",
                "sessionId", sessionId,
                "submittedAt", Instant.now().toString(),
                "message", "Your exam has been submitted successfully."
        ));
    }
}
