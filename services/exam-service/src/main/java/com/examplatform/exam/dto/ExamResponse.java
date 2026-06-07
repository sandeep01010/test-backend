package com.examplatform.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class ExamResponse {
    private UUID id;
    private String title;
    private String examType;
    private int durationMins;
    private int totalMarks;
    private String status;
    private Instant startTime;
    private Instant endTime;
}
