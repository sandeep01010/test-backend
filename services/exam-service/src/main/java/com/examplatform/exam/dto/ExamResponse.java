package com.examplatform.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class ExamResponse {
    private UUID id;
    private String title;
    private String description;
    private String examType;
    private String category;        // category_code
    private String testType;        // FULL_MOCK / PREVIOUS_YEAR / ...
    private int totalQuestions;
    private int durationMins;
    private int totalMarks;
    private String status;
    private boolean attempted;      // per-student (set in list endpoints)
    private Instant startTime;
    private Instant endTime;
    private String instructions;
    private boolean locked;         // requires an active category/group access grant to attempt
    private boolean hasAccess;      // per-student (set in list endpoints) — true if not locked, or locked but owned
}
