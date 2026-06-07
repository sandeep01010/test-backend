package com.examplatform.testengine.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ServerAck {
    private String type;        // ACK | WARNING | ERROR | TIME_UP
    private String sessionId;
    private int savedAnswerCount;
    private long serverTime;
    private long latencyMs;
    private String message;
}
