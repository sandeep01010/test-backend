package com.examplatform.exam.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExcelUploadRequest {
    private String categoryCode;   // e.g. JEE_MAIN
    private String testType;       // FULL_MOCK | PREVIOUS_YEAR | SUBJECT_WISE | CHAPTER_WISE
    private String title;
    private String description;
    private int    durationMins;
    private int    totalMarks;
    private String instructions;
}
