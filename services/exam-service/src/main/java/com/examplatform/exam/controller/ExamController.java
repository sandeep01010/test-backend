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
