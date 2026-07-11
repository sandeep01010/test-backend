package com.examplatform.exam.controller;

import com.examplatform.exam.dto.*;
import com.examplatform.exam.dto.ExamPreviewResponse;
import com.examplatform.exam.service.ExamService;
import com.examplatform.exam.service.ExamUploadService;
import com.examplatform.exam.service.ImageStorageService;
import com.examplatform.exam.service.PaperGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final ExamUploadService uploadService;
    private final PaperGenerationService paperService;
    private final ImageStorageService imageStorageService;

    /**
     * POST /exams/images/upload — Admin uploads a question/option image.
     * Returns { "url": "..." } pointing to the served file.
     */
    @PostMapping(value = "/images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file) throws IOException {
        String url = imageStorageService.store(file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * POST /exams/images/bulk-upload — Upload multiple images to Azure Blob in one request.
     * Returns [{name, url}, ...] for each uploaded file.
     */
    @PostMapping(value = "/images/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<Map<String, String>>> bulkUploadImages(
            @RequestParam("files") List<MultipartFile> files) {
        List<Map<String, String>> results = files.stream()
                .filter(f -> !f.isEmpty())
                .map(f -> {
                    try {
                        String originalName = f.getOriginalFilename() != null ? f.getOriginalFilename() : "image";
                        String url = imageStorageService.storeWithName(f, stripExt(originalName));
                        return Map.of("name", originalName, "url", url, "status", "ok");
                    } catch (Exception e) {
                        String originalName = f.getOriginalFilename() != null ? f.getOriginalFilename() : "image";
                        return Map.of("name", originalName, "url", "", "status", "error: " + e.getMessage());
                    }
                })
                .toList();
        return ResponseEntity.ok(results);
    }

    private String stripExt(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    /**
     * POST /exams/upload  (multipart/form-data)
     * Admin uploads an Excel file → exam saved as DRAFT for preview/approval.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ExcelUploadResponse> uploadExcel(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("userId") UUID adminId) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(uploadService.uploadExam(file, adminId));
    }

    /**
     * GET /exams/{examId}/preview
     * Returns full paper with correct answers — admin/super_admin only.
     */
    @GetMapping("/{examId}/preview")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ExamPreviewResponse> previewExam(
            @PathVariable UUID examId,
            @RequestAttribute("userId") UUID adminId,
            @RequestAttribute(value = "userRole", required = false) String userRole) {
        return ResponseEntity.ok(uploadService.previewExam(examId, adminId, isSuperAdmin(userRole)));
    }

    /**
     * POST /exams/{examId}/approve — Publish a DRAFT exam.
     */
    @PostMapping("/{examId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> approveExam(
            @PathVariable UUID examId,
            @RequestAttribute("userId") UUID adminId,
            @RequestAttribute(value = "userRole", required = false) String userRole) {
        uploadService.approveExam(examId, adminId, isSuperAdmin(userRole));
        return ResponseEntity.ok(Map.of("status", "PUBLISHED", "message", "Exam published successfully."));
    }

    /**
     * DELETE /exams/{examId} — Delete a DRAFT/PUBLISHED exam and its questions.
     */
    @DeleteMapping("/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> deleteExam(
            @PathVariable UUID examId,
            @RequestAttribute("userId") UUID adminId,
            @RequestAttribute(value = "userRole", required = false) String userRole) {
        uploadService.deleteExam(examId, adminId, isSuperAdmin(userRole));
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /exams/{examId}/lock — toggle whether this exam requires an active access
     * grant (category/group purchase) to attempt. Admin decides WHICH papers are premium;
     * Super Admin separately controls HOW MUCH unlocking them costs via the price endpoints.
     */
    @PatchMapping("/{examId}/lock")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ExamResponse> setLocked(
            @PathVariable UUID examId,
            @RequestBody Map<String, Boolean> body) {
        boolean locked = Boolean.TRUE.equals(body.get("locked"));
        return ResponseEntity.ok(examService.setLocked(examId, locked));
    }

    private static boolean isSuperAdmin(String role) {
        return "SUPER_ADMIN".equalsIgnoreCase(role);
    }

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

    /** Admin: exams I created. SUPER_ADMIN sees all exams. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<ExamResponse>> myExams(
            @RequestAttribute("userId") UUID adminId,
            @RequestAttribute(value = "userRole", required = false) String userRole) {
        boolean isSuperAdmin = "SUPER_ADMIN".equalsIgnoreCase(userRole);
        return ResponseEntity.ok(examService.listByAdmin(adminId, isSuperAdmin));
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

    /**
     * Internal service-to-service endpoint — returns only question IDs for a student's paper.
     * No JWT required; only accessible within the internal network.
     */
    @GetMapping("/internal/{examId}/paper-ids")
    public ResponseEntity<List<String>> getPaperQuestionIds(
            @PathVariable UUID examId,
            @RequestParam UUID studentId) {
        List<String> ids = paperService.getOrGeneratePaper(examId, studentId)
                .stream()
                .map(PaperGenerationService.PaperQuestion::getQuestionId)
                .toList();
        return ResponseEntity.ok(ids);
    }

    /**
     * Serve uploaded question/option images from local filesystem.
     */
    @GetMapping("/images/{filename:.+}")
    public ResponseEntity<byte[]> serveImage(@PathVariable String filename) throws IOException {
        Path file = imageStorageService.resolve(filename);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        String ct = Files.probeContentType(file);
        MediaType mediaType = ct != null ? MediaType.parseMediaType(ct) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .body(Files.readAllBytes(file));
    }
}
