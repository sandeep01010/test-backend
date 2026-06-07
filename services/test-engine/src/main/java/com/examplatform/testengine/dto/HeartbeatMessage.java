package com.examplatform.testengine.dto;

import lombok.Data;
import java.util.Map;

@Data
public class HeartbeatMessage {
    private String type;          // "HEARTBEAT"
    private Map<String, Map<String, Object>> answers;  // questionId → {answer, timeSpent, markedForReview}
    private long timeRemaining;   // client-side remaining (server recomputes)
    private long timestamp;       // client epoch millis
}
