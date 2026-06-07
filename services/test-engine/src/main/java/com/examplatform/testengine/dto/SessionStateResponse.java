package com.examplatform.testengine.dto;

import com.examplatform.testengine.model.SessionState;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;

@Data @Builder
public class SessionStateResponse {
    private String sessionId;
    private String status;
    private long timeRemainingSecs;
    private Map<String, SessionState.AnswerEntry> answers;
    private Instant startedAt;
    private Instant lastSavedAt;

    public static SessionStateResponse from(SessionState state) {
        return SessionStateResponse.builder()
                .sessionId(state.getSessionId())
                .status(state.getStatus())
                .timeRemainingSecs(state.getTimeRemainingSecs())
                .answers(state.getAnswers())
                .startedAt(state.getStartedAt())
                .lastSavedAt(state.getLastSavedAt())
                .build();
    }
}
