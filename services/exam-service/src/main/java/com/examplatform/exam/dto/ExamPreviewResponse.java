package com.examplatform.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data @Builder
public class ExamPreviewResponse {
    private UUID   examId;
    private String title;
    private String categoryCode;
    private String testType;
    private String status;
    private int    durationMins;
    private int    totalMarks;
    private int    totalQuestions;
    private String instructions;
    private List<PreviewSection> sections;

    @Data @Builder
    public static class PreviewSection {
        private String sectionName;
        private String subject;
        private List<PreviewQuestion> questions;
    }

    @Data @Builder
    public static class PreviewQuestion {
        private String questionId;
        private int    qNo;
        private String type;
        private String subject;
        private String topic;
        private String chapter;
        private String difficulty;
        private String questionText;
        private String questionHtml;
        private List<String> questionImageUrls;
        private List<OptionDto> options;
        private List<MatchItemDto> matchListLeft;   // MATCH_THE_FOLLOWING only — List-I
        private List<MatchItemDto> matchListRight;  // MATCH_THE_FOLLOWING only — List-II
        private String correctAnswer;
        private List<String> correctAnswers;
        private Double numericalMin;
        private Double numericalMax;
        private String explanation;
        private double marks;
        private double negativeMarks;
        private List<String> tags;
    }

    @Data @Builder
    public static class OptionDto {
        private String id;
        private String text;
        private String imageUrl;
    }

    @Data @Builder
    public static class MatchItemDto {
        private String label;
        private String text;
    }
}
