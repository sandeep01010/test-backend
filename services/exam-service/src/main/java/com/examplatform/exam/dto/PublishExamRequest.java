package com.examplatform.exam.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.Instant;

@Data
public class PublishExamRequest {
    @NotNull
    private Instant startTime;
    @NotNull
    private Instant endTime;
    @Positive
    private int expectedStudents;
}
