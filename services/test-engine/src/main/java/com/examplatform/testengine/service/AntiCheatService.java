package com.examplatform.testengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AntiCheatService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String RISK_KEY     = "anticheat:risk:%s";
    private static final String EVENTS_KEY   = "anticheat:events:%s";
    private static final int    TTL_HOURS    = 48;

    // Risk score weights per event type
    private static final Map<String, Integer> EVENT_WEIGHTS = Map.of(
        "TAB_SWITCH",      15,
        "COPY_PASTE",      10,
        "FULLSCREEN_EXIT", 10,
        "RIGHT_CLICK",      5,
        "DEV_TOOLS",       25,
        "MULTIPLE_IP",     30,
        "FAST_ANSWER",      5
    );

    public void recordEvent(String sessionId, String eventType, Object metadata) {
        int weight = EVENT_WEIGHTS.getOrDefault(eventType, 5);

        // Increment risk score
        Long newScore = redisTemplate.opsForValue()
                .increment(String.format(RISK_KEY, sessionId), weight);
        redisTemplate.expire(String.format(RISK_KEY, sessionId), Duration.ofHours(TTL_HOURS));

        // Log event to list
        String eventJson = String.format("{\"type\":\"%s\",\"ts\":\"%s\",\"meta\":%s}",
                eventType, Instant.now(), metadata);
        redisTemplate.opsForList().rightPush(String.format(EVENTS_KEY, sessionId), eventJson);
        redisTemplate.expire(String.format(EVENTS_KEY, sessionId), Duration.ofHours(TTL_HOURS));

        // Publish to Kafka for admin monitoring + MongoDB persistence
        kafkaTemplate.send("anti-cheat-events", sessionId,
                Map.of("sessionId", sessionId, "eventType", eventType,
                       "riskScore", newScore, "timestamp", Instant.now().toString()));

        log.info("Anti-cheat event: session={} type={} risk={}", sessionId, eventType, newScore);
    }

    public int getRiskScore(String sessionId) {
        Object val = redisTemplate.opsForValue().get(String.format(RISK_KEY, sessionId));
        if (val == null) return 0;
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean isHighRisk(String sessionId) {
        return getRiskScore(sessionId) >= 70;
    }
}
