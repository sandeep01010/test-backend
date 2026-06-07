package com.examplatform.testengine.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data @Builder
public class SessionStartResponse {
    private String sessionId;
    private String status;
    private long timeRemainingSecs;
    private Instant startedAt;
}
