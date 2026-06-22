package com.examplatform.exam.controller;

import com.examplatform.exam.dto.*;
import com.examplatform.exam.model.Exam;
import com.examplatform.exam.service.CategoryGroupService;
import com.examplatform.exam.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Combined category groups (e.g. "JEE Mains + Advanced") — aggregates papers from several
 * exam categories into one browsable group, without ever changing an exam's own category.
 * Management (create/edit/membership/deactivate) is SUPER_ADMIN only; reads are public,
 * same as /exams/categories.
 *
 * Mounted under /exams/category-groups so it routes through the existing api-gateway
 * rule (/api/v1/exams/**).
 */
@RestController
@RequestMapping("/exams/category-groups")
@RequiredArgsConstructor
public class CategoryGroupController {

    private final CategoryGroupService groupService;
    private final ExamService examService;

    /** Public: active groups (used by student dashboard + filters). */
    @GetMapping
    public ResponseEntity<List<CategoryGroupDto>> list() {
        return ResponseEntity.ok(groupService.listActive());
    }

    /** Public: per-group counts grouped by test type, summed across member categories. */
    @GetMapping("/summary")
    public ResponseEntity<List<CategoryGroupSummaryResponse>> summary(
            @RequestAttribute(value = "userId", required = false) UUID studentId) {
        return ResponseEntity.ok(groupService.summaries(studentId));
    }

    /**
     * Public: the union of papers from every member category for this group + test type.
     * Each returned exam still carries its own original "category" code, so the frontend
     * can show which underlying category a paper actually came from.
     */
    @GetMapping("/{code}/exams")
    public ResponseEntity<List<ExamResponse>> exams(
            @PathVariable String code,
            @RequestParam(required = false) com.examplatform.exam.model.TestType testType,
            @RequestParam(required = false) Exam.ExamStatus status,
            @RequestAttribute(value = "userId", required = false) UUID studentId) {
        List<String> members = groupService.resolveMemberCodes(code);
        Exam.ExamStatus effectiveStatus = status != null ? status : Exam.ExamStatus.PUBLISHED;
        return ResponseEntity.ok(examService.listExamsForCategories(members, testType, effectiveStatus, studentId));
    }

    /** Super-admin: list ALL groups incl. inactive (management view). */
    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<CategoryGroupDto>> listAll() {
        return ResponseEntity.ok(groupService.listAll());
    }

    /** Public: single group by code — used by payment-service to fetch the
     *  authoritative bundle price. */
    @GetMapping("/{code}")
    public ResponseEntity<CategoryGroupDto> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(groupService.getByCode(code));
    }

    /** Super-admin only: set this group's 1-year bundle price. */
    @PutMapping("/{code}/price")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<CategoryGroupDto> updatePrice(
            @PathVariable String code, @Valid @RequestBody UpdatePriceRequest req) {
        return ResponseEntity.ok(groupService.updatePrice(code, req.getPriceInPaise()));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<CategoryGroupDto> create(@Valid @RequestBody UpsertCategoryGroupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.create(req));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<CategoryGroupDto> update(
            @PathVariable String code, @Valid @RequestBody UpsertCategoryGroupRequest req) {
        return ResponseEntity.ok(groupService.update(code, req));
    }

    /** Soft-delete (deactivate) — keeps member categories/exams intact. */
    @DeleteMapping("/{code}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable String code) {
        groupService.deactivate(code);
        return ResponseEntity.noContent().build();
    }
}
