package com.examplatform.testengine.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class SaveAnswerRequest {
    @NotBlank private String questionId;
    private List<String> answer;    // null or empty = clear answer; MCQ=["A"], numerical=["42"]
    private boolean markedForReview;
    private long timeSpentSecs;
}
