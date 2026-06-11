package com.examplatform.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** Returned when a student starts (or re-starts) an exam attempt. */
@Data @Builder
public class AttemptResponse {
    private UUID enrollmentId;
    private UUID examId;
    private int attemptNo;        // 1 for first attempt, 2+ for re-attempts
    private int durationMins;
    private boolean reattempt;    // true if a prior attempt already existed
}
