package com.examplatform.exam.service;

import com.examplatform.exam.dto.*;
import com.examplatform.exam.model.ExamCategory;
import com.examplatform.exam.model.TestType;
import com.examplatform.exam.repository.EnrollmentRepository;
import com.examplatform.exam.repository.ExamCategoryRepository;
import com.examplatform.exam.repository.ExamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamCategoryService {

    private final ExamCategoryRepository categoryRepository;
    private final ExamRepository examRepository;
    private final EnrollmentRepository enrollmentRepository;

    // ── Public reads ──────────────────────────────────────────────────────────

    public List<CategoryDto> listActive() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .stream().map(this::toDto).toList();
    }

    public List<CategoryDto> listAll() {
        return categoryRepository.findAllByOrderByDisplayOrderAsc()
                .stream().map(this::toDto).toList();
    }

    /**
     * Per-category counts grouped by test type, for the student dashboard.
     * Counts only PUBLISHED exams. attemptedCount is per-student.
     */
    public List<CategorySummaryResponse> summaries(UUID studentId) {
        // category_code -> (test_type -> count)
        Map<String, Map<String, Long>> counts = new HashMap<>();
        for (Object[] row : examRepository.countPublishedByCategoryAndType()) {
            String cat  = (String) row[0];
            Object type = row[1];
            long   n    = ((Number) row[2]).longValue();
            if (cat == null) continue;
            counts.computeIfAbsent(cat, k -> new HashMap<>())
                  .put(type == null ? "OTHER" : type.toString(), n);
        }

        // per-student attempted counts by category
        Map<String, Long> attempted = new HashMap<>();
        if (studentId != null) {
            for (Object[] row : examRepository.countAttemptedByCategory(studentId)) {
                if (row[0] != null) attempted.put((String) row[0], ((Number) row[1]).longValue());
            }
        }

        List<CategorySummaryResponse> out = new ArrayList<>();
        for (ExamCategory cat : categoryRepository.findByActiveTrueOrderByDisplayOrderAsc()) {
            Map<String, Long> byType = new LinkedHashMap<>();
            long total = 0;
            Map<String, Long> raw = counts.getOrDefault(cat.getCode(), Map.of());
            for (TestType tt : TestType.values()) {
                long n = raw.getOrDefault(tt.name(), 0L);
                byType.put(tt.name(), n);
                total += n;
            }
            out.add(CategorySummaryResponse.builder()
                    .category(cat.getCode())
                    .title(cat.getTitle())
                    .tag(cat.getTag())
                    .color(cat.getColor())
                    .counts(byType)
                    .totalTests(total)
                    .attemptedCount(attempted.getOrDefault(cat.getCode(), 0L))
                    .build());
        }
        return out;
    }

    // ── Admin writes ──────────────────────────────────────────────────────────

    @Transactional
    public CategoryDto create(UpsertCategoryRequest req) {
        if (req.getCode() == null || req.getCode().isBlank())
            throw new IllegalArgumentException("code is required");
        if (categoryRepository.existsById(req.getCode()))
            throw new IllegalStateException("Category already exists: " + req.getCode());

        ExamCategory cat = ExamCategory.builder()
                .code(req.getCode())
                .title(req.getTitle())
                .tag(req.getTag())
                .color(req.getColor())
                .description(req.getDescription())
                .displayOrder(req.getDisplayOrder())
                .active(req.isActive())
                .build();
        cat = categoryRepository.save(cat);
        log.info("Category created: {}", cat.getCode());
        return toDto(cat);
    }

    @Transactional
    public CategoryDto update(String code, UpsertCategoryRequest req) {
        ExamCategory cat = categoryRepository.findById(code)
                .orElseThrow(() -> new RuntimeException("Category not found: " + code));
        cat.setTitle(req.getTitle());
        cat.setTag(req.getTag());
        cat.setColor(req.getColor());
        cat.setDescription(req.getDescription());
        cat.setDisplayOrder(req.getDisplayOrder());
        cat.setActive(req.isActive());
        return toDto(categoryRepository.save(cat));
    }

    /** Soft-delete: deactivate so existing exams keep their category reference. */
    @Transactional
    public void deactivate(String code) {
        ExamCategory cat = categoryRepository.findById(code)
                .orElseThrow(() -> new RuntimeException("Category not found: " + code));
        cat.setActive(false);
        categoryRepository.save(cat);
    }

    private CategoryDto toDto(ExamCategory c) {
        return CategoryDto.builder()
                .code(c.getCode()).title(c.getTitle()).tag(c.getTag())
                .color(c.getColor()).description(c.getDescription())
                .displayOrder(c.getDisplayOrder()).active(c.isActive())
                .build();
    }
}
