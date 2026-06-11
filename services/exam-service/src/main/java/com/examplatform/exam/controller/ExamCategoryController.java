package com.examplatform.exam.controller;

import com.examplatform.exam.dto.*;
import com.examplatform.exam.service.ExamCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Exam categories — the extensibility surface.
 * Adding a new exam track (e.g. GATE, SAT) = POST here from the Admin UI.
 *
 * Mounted under /exams/categories so it routes through the existing
 * api-gateway rule (/api/v1/exams/**).
 */
@RestController
@RequestMapping("/exams/categories")
@RequiredArgsConstructor
public class ExamCategoryController {

    private final ExamCategoryService categoryService;

    /** Public: active categories (used by student dashboard + filters). */
    @GetMapping
    public ResponseEntity<List<CategoryDto>> list() {
        return ResponseEntity.ok(categoryService.listActive());
    }

    /** Public: per-category counts grouped by test type, for the dashboard. */
    @GetMapping("/summary")
    public ResponseEntity<List<CategorySummaryResponse>> summary(
            @RequestAttribute(value = "userId", required = false) UUID studentId) {
        return ResponseEntity.ok(categoryService.summaries(studentId));
    }

    /** Admin: list ALL categories incl. inactive (management view). */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<CategoryDto>> listAll() {
        return ResponseEntity.ok(categoryService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<CategoryDto> create(@Valid @RequestBody UpsertCategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(req));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<CategoryDto> update(
            @PathVariable String code, @Valid @RequestBody UpsertCategoryRequest req) {
        return ResponseEntity.ok(categoryService.update(code, req));
    }

    /** Soft-delete (deactivate) — keeps existing exams' references intact. */
    @DeleteMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable String code) {
        categoryService.deactivate(code);
        return ResponseEntity.noContent().build();
    }
}
