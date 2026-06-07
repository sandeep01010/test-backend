package com.examplatform.testengine.websocket;

import com.examplatform.testengine.dto.HeartbeatMessage;
import com.examplatform.testengine.dto.ServerAck;
import com.examplatform.testengine.service.AntiCheatService;
import com.examplatform.testengine.service.SessionStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.Instant;

/**
 * STOMP WebSocket controller for real-time exam interactions.
 *
 * Client subscribes to: /topic/session/{sessionId}
 * Client sends to:      /app/exam/{sessionId}/heartbeat
 *                       /app/exam/{sessionId}/anticheat
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ExamWebSocketController {

    private final SessionStateService sessionService;
    private final AntiCheatService antiCheatService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Process heartbeat from student every 5 seconds.
     * Contains latest answer state + time remaining.
     */
    @MessageMapping("/exam/{sessionId}/heartbeat")
    public void handleHeartbeat(
            @DestinationVariable String sessionId,
            @Payload HeartbeatMessage heartbeat) {

        long start = System.currentTimeMillis();

        // Save answers to Redis (< 2ms)
        if (heartbeat.getAnswers() != null && !heartbeat.getAnswers().isEmpty()) {
            sessionService.processBulkHeartbeat(
                    sessionId,
                    heartbeat.getAnswers(),
                    heartbeat.getTimeRemaining());
        }

        long latencyMs = System.currentTimeMillis() - start;

        // Send ACK back to student
        ServerAck ack = ServerAck.builder()
                .type("ACK")
                .sessionId(sessionId)
                .savedAnswerCount(heartbeat.getAnswers() != null ? heartbeat.getAnswers().size() : 0)
                .serverTime(Instant.now().toEpochMilli())
                .latencyMs(latencyMs)
                .build();

        messagingTemplate.convertAndSend("/topic/session/" + sessionId, ack);
    }

    /**
     * Receive anti-cheat events from client (tab switch, fullscreen exit, etc.)
     */
    @MessageMapping("/exam/{sessionId}/anticheat")
    public void handleAntiCheatEvent(
            @DestinationVariable String sessionId,
            @Payload AntiCheatEvent event) {

        antiCheatService.recordEvent(sessionId, event.getType(), event.getMetadata());

        // If risk score too high, send warning
        int riskScore = antiCheatService.getRiskScore(sessionId);
        if (riskScore >= 80) {
            messagingTemplate.convertAndSend("/topic/session/" + sessionId,
                    ServerAck.builder()
                            .type("WARNING")
                            .message("Suspicious activity detected. Exam may be terminated.")
                            .build());
        }
    }

    @lombok.Data
    public static class AntiCheatEvent {
        private String type;     // TAB_SWITCH, COPY_PASTE, FULLSCREEN_EXIT, RIGHT_CLICK
        private Object metadata;
    }
}
