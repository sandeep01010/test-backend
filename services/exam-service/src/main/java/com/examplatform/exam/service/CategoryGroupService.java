package com.examplatform.exam.service;

import com.examplatform.exam.dto.CategoryGroupDto;
import com.examplatform.exam.dto.CategoryGroupSummaryResponse;
import com.examplatform.exam.dto.UpsertCategoryGroupRequest;
import com.examplatform.exam.model.CategoryGroup;
import com.examplatform.exam.model.TestType;
import com.examplatform.exam.repository.CategoryGroupRepository;
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
public class CategoryGroupService {

    private final CategoryGroupRepository groupRepository;
    private final ExamCategoryRepository categoryRepository;
    private final ExamRepository examRepository;

    // ── Public reads ──────────────────────────────────────────────────────────

    public List<CategoryGroupDto> listActive() {
        return groupRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .stream().map(this::toDto).toList();
    }

    public List<CategoryGroupDto> listAll() {
        return groupRepository.findAllByOrderByDisplayOrderAsc()
                .stream().map(this::toDto).toList();
    }

    /** Single group by code — used by payment-service to fetch the authoritative bundle price. */
    public CategoryGroupDto getByCode(String code) {
        return groupRepository.findById(code)
                .map(this::toDto)
                .orElseThrow(() -> new RuntimeException("Category group not found: " + code));
    }

    /**
     * Per-group counts grouped by test type, summed across each group's active member
     * categories — same per-test-type/published/attempted semantics as
     * ExamCategoryService.summaries(), just aggregated over several categories.
     */
    public List<CategoryGroupSummaryResponse> summaries(UUID studentId) {
        // category_code -> (test_type -> count) — same raw data ExamCategoryService uses.
        Map<String, Map<String, Long>> countsByCategory = new HashMap<>();
        for (Object[] row : examRepository.countPublishedByCategoryAndType()) {
            String cat  = (String) row[0];
            Object type = row[1];
            long   n    = ((Number) row[2]).longValue();
            if (cat == null) continue;
            countsByCategory.computeIfAbsent(cat, k -> new HashMap<>())
                  .put(type == null ? "OTHER" : type.toString(), n);
        }

        Map<String, Long> attemptedByCategory = new HashMap<>();
        if (studentId != null) {
            for (Object[] row : examRepository.countAttemptedByCategory(studentId)) {
                if (row[0] != null) attemptedByCategory.put((String) row[0], ((Number) row[1]).longValue());
            }
        }

        // Only count members that are still active categories — a deactivated category
        // shouldn't keep contributing to a group's visible totals.
        Set<String> activeCategoryCodes = new HashSet<>();
        categoryRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .forEach(c -> activeCategoryCodes.add(c.getCode()));

        List<CategoryGroupSummaryResponse> out = new ArrayList<>();
        for (CategoryGroup group : groupRepository.findByActiveTrueOrderByDisplayOrderAsc()) {
            List<String> members = group.getMemberCodes().stream()
                    .filter(activeCategoryCodes::contains).sorted().toList();

            Map<String, Long> byType = new LinkedHashMap<>();
            long total = 0;
            for (TestType tt : TestType.values()) {
                long n = 0;
                for (String member : members) {
                    n += countsByCategory.getOrDefault(member, Map.of()).getOrDefault(tt.name(), 0L);
                }
                byType.put(tt.name(), n);
                total += n;
            }

            long attempted = 0;
            for (String member : members) {
                attempted += attemptedByCategory.getOrDefault(member, 0L);
            }

            out.add(CategoryGroupSummaryResponse.builder()
                    .category(group.getCode())
                    .title(group.getTitle())
                    .tag(group.getTag())
                    .color(group.getColor())
                    .counts(byType)
                    .totalTests(total)
                    .attemptedCount(attempted)
                    .memberCodes(members)
                    .priceInPaise(group.getPriceInPaise())
                    .build());
        }
        return out;
    }

    // ── Admin writes (Super Admin only — enforced at the controller) ──────────────

    @Transactional
    public CategoryGroupDto create(UpsertCategoryGroupRequest req) {
        if (req.getCode() == null || req.getCode().isBlank())
            throw new IllegalArgumentException("code is required");
        if (groupRepository.existsById(req.getCode()))
            throw new IllegalStateException("Category group already exists: " + req.getCode());

        CategoryGroup group = CategoryGroup.builder()
                .code(req.getCode())
                .title(req.getTitle())
                .tag(req.getTag())
                .color(req.getColor())
                .description(req.getDescription())
                .displayOrder(req.getDisplayOrder())
                .active(req.isActive())
                .memberCodes(new HashSet<>(validMembers(req.getMemberCodes())))
                .build();
        group = groupRepository.save(group);
        log.info("Category group created: {} with members {}", group.getCode(), group.getMemberCodes());
        return toDto(group);
    }

    @Transactional
    public CategoryGroupDto update(String code, UpsertCategoryGroupRequest req) {
        CategoryGroup group = groupRepository.findById(code)
                .orElseThrow(() -> new RuntimeException("Category group not found: " + code));
        group.setTitle(req.getTitle());
        group.setTag(req.getTag());
        group.setColor(req.getColor());
        group.setDescription(req.getDescription());
        group.setDisplayOrder(req.getDisplayOrder());
        group.setActive(req.isActive());
        // Full replacement, so removing a category from the admin's selection actually removes it.
        group.setMemberCodes(new HashSet<>(validMembers(req.getMemberCodes())));
        return toDto(groupRepository.save(group));
    }

    /** Soft-delete: deactivate so the group disappears from student dashboards without
     *  touching the underlying exams/categories it referenced. */
    @Transactional
    public void deactivate(String code) {
        CategoryGroup group = groupRepository.findById(code)
                .orElseThrow(() -> new RuntimeException("Category group not found: " + code));
        group.setActive(false);
        groupRepository.save(group);
    }

    /** Resolve a group's member category codes, for the combined exam-listing endpoint. */
    public List<String> resolveMemberCodes(String groupCode) {
        return groupRepository.findById(groupCode)
                .map(g -> List.copyOf(g.getMemberCodes()))
                .orElseThrow(() -> new RuntimeException("Category group not found: " + groupCode));
    }

    /** Super-admin only bundle-price update — separate from update() for the same reason
     *  as ExamCategoryService.updatePrice(). */
    @Transactional
    public CategoryGroupDto updatePrice(String code, long priceInPaise) {
        CategoryGroup group = groupRepository.findById(code)
                .orElseThrow(() -> new RuntimeException("Category group not found: " + code));
        group.setPriceInPaise(priceInPaise);
        return toDto(groupRepository.save(group));
    }

    private List<String> validMembers(List<String> requested) {
        if (requested == null || requested.isEmpty()) return List.of();
        Set<String> existing = new HashSet<>();
        categoryRepository.findAll().forEach(c -> existing.add(c.getCode()));
        return requested.stream().filter(existing::contains).distinct().toList();
    }

    private CategoryGroupDto toDto(CategoryGroup g) {
        return CategoryGroupDto.builder()
                .code(g.getCode()).title(g.getTitle()).tag(g.getTag())
                .color(g.getColor()).description(g.getDescription())
                .displayOrder(g.getDisplayOrder()).active(g.isActive())
                .memberCodes(g.getMemberCodes().stream().sorted().toList())
                .priceInPaise(g.getPriceInPaise())
                .build();
    }
}
