package com.examplatform.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** One category with per-test-type counts, for the student dashboard. */
@Data @Builder
public class CategorySummaryResponse {
    private String category;            // code, e.g. JEE_MAIN
    private String title;
    private String tag;
    private String color;
    private Map<String, Long> counts;   // { FULL_MOCK: 15, PREVIOUS_YEAR: 18, ... }
    private long totalTests;
    private long attemptedCount;
}
