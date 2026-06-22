package com.examplatform.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Same shape as CategorySummaryResponse, plus memberCodes — counts are summed across
 *  every active member category, by test type, for the student dashboard. */
@Data @Builder
public class CategoryGroupSummaryResponse {
    private String category;            // group code, e.g. JEE_MAIN_ADVANCED
    private String title;
    private String tag;
    private String color;
    private Map<String, Long> counts;   // { FULL_MOCK: 33, PREVIOUS_YEAR: 19, ... } summed across members
    private long totalTests;
    private long attemptedCount;
    private List<String> memberCodes;
    private long priceInPaise;          // the group's OWN bundle price, independent of members'
}
