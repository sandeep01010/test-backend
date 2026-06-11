package com.examplatform.exam.controller;

import com.examplatform.exam.dto.*;
import com.examplatform.exam.service.ExamService;
import com.examplatform.exam.service.PaperGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final PaperGenerationService paperService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ExamResponse> createExam(
            @Valid @RequestBody CreateExamRequest req,
            @RequestAttribute("userId") UUID adminId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(examService.createExam(req, adminId));
    }

    /**
     * List/browse exams with optional filters.
     * GET /exams?category=JEE_MAIN&testType=FULL_MOCK&status=PUBLISHED
     */
    @GetMapping
    public ResponseEntity<List<ExamResponse>> listExams(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) com.examplatform.exam.model.TestType testType,
            @RequestParam(required = false, defaultValue = "PUBLISHED") String status,
            @RequestAttribute(value = "userId", required = false) UUID studentId) {

        com.examplatform.exam.model.Exam.ExamStatus st =
                (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                        ? null
                        : com.examplatform.exam.model.Exam.ExamStatus.valueOf(status.toUpperCase());

        return ResponseEntity.ok(examService.listExams(category, testType, st, studentId));
    }

    /** Admin: exams I created. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<ExamResponse>> myExams(
            @RequestAttribute("userId") UUID adminId) {
        return ResponseEntity.ok(examService.listByAdmin(adminId));
    }

    /** Super-admin: aggregate analytics. */
    @GetMapping("/admin/analytics")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> analytics() {
        return ResponseEntity.ok(examService.analytics());
    }

    @GetMapping("/{examId}")
    public ResponseEntity<ExamResponse> getExam(@PathVariable UUID examId) {
        return ResponseEntity.ok(examService.getExam(examId));
    }

    @PostMapping("/{examId}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ExamResponse> publishExam(
            @PathVariable UUID examId,
            @Valid @RequestBody PublishExamRequest req,
            @RequestAttribute("userId") UUID adminId) {
        return ResponseEntity.ok(examService.publishExam(examId, adminId, req));
    }

    /**
     * Start (or re-start) a practice attempt — no slot booking.
     * Idempotently ensures an enrollment exists and bumps the attempt counter.
     * Called by the "Attempt" / "Re-attempt" buttons.
     */
    @PostMapping("/{examId}/attempt")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<AttemptResponse> attempt(
            @PathVariable UUID examId,
            @RequestAttribute("userId") UUID studentId) {
        return ResponseEntity.ok(examService.ensureAttempt(examId, studentId));
    }

    @PostMapping("/{examId}/enroll")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<EnrollmentResponse> enroll(
            @PathVariable UUID examId,
            @RequestParam UUID slotId,
            @RequestAttribute("userId") UUID studentId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(examService.enrollStudent(examId, studentId, slotId));
    }

    @GetMapping("/{examId}/slots")
    public ResponseEntity<List<SlotResponse>> getSlots(@PathVariable UUID examId) {
        return ResponseEntity.ok(examService.getAvailableSlots(examId));
    }

    /**
     * Get the student's generated paper for an exam session.
     * Returns questions WITHOUT correct answers.
     */
    @GetMapping("/{examId}/paper/{sessionId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<PaperGenerationService.PaperQuestion>> getPaper(
            @PathVariable UUID examId,
            @PathVariable String sessionId,
            @RequestAttribute("userId") UUID studentId) {
        return ResponseEntity.ok(paperService.getOrGeneratePaper(examId, studentId));
    }
}
