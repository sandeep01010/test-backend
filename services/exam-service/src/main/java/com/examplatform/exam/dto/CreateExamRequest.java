package com.examplatform.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class CreateExamRequest {

    @NotBlank
    @Size(max = 500)
    private String title;

    private String description;

    @NotBlank
    private String examType;

    /** Category code (e.g. JEE_MAIN). Must reference an existing exam_categories row. */
    @NotBlank
    private String category;

    /** FULL_MOCK | PREVIOUS_YEAR | SUBJECT_WISE | CHAPTER_WISE */
    @NotNull
    private com.examplatform.exam.model.TestType testType;

    @Positive
    private int durationMins;

    @Positive
    private int totalMarks;

    @Min(0)
    private double negativeMarks;

    private String instructions;

    private boolean shuffleQuestions = true;
    private boolean showResultImmediately = false;

    @Valid
    @NotEmpty
    private List<SectionDto> sections;

    @Data
    public static class SectionDto {
        @NotBlank
        private String name;
        @NotBlank
        private String subject;
        @Positive
        private int maxQuestions;
        @Positive
        private double marksPerQ;
        @Min(0)
        private double negativeMarks;
    }
}
