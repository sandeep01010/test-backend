package com.examplatform.testengine.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveAnswerRequest {
    @NotBlank private String questionId;
    private String answer;          // null = clear answer
    private boolean markedForReview;
    private long timeSpentSecs;
}
