package com.examplatform.testengine.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.util.UUID;

@Data
public class StartSessionRequest {
    @NotNull private UUID examId;
    @NotNull private UUID enrollmentId;
    @Positive private long durationSeconds;
}
