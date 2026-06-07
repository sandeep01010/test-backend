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
