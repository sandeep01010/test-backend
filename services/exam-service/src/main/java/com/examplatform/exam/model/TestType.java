package com.examplatform.exam.model;

/**
 * Kind of test within a category. Stable, small set — kept as an enum.
 * (Categories are data-driven; test types rarely change.)
 */
public enum TestType {
    FULL_MOCK,
    PREVIOUS_YEAR,
    SUBJECT_WISE,
    CHAPTER_WISE
}
