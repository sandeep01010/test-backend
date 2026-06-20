package com.examplatform.testengine.service;

import com.examplatform.testengine.model.SessionState;
import com.examplatform.testengine.repository.SessionStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Note: List<String> answer type in saveAnswer signature
import java.util.List;

/**
 * Core service managing real-time exam session state.
 *
 * Write path:   Client → WS → Redis (5s) → Kafka → MongoDB (15s)
 * Recovery path: Redis miss → MongoDB → re-populate Redis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionStateService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SessionStateRepository mongoRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // In-memory dirty set: tracks sessions modified since last MongoDB flush
    private final Set<String> dirtySessions = ConcurrentHashMap.newKeySet();

    private static final String SESSION_KEY = "exam:session:%s:state";
    private static final String SESSION_TTL_HOURS = "2";

    // Global live-monitoring counters (read by /sessions/metrics/live)
    static final String GLOBAL_ACTIVE    = "metrics:sessions:active";
    static final String GLOBAL_STARTED   = "metrics:sessions:started_total";
    static final String GLOBAL_SUBMITTED = "metrics:sessions:submitted_total";
    private static final String ANS_BUCKET = "metrics:ans:";   // + epochSecond
    private static final int ANS_BUCKET_TTL = 20;

    // -------------------------------------------------------------------------
    // Session Lifecycle
    // -------------------------------------------------------------------------

    public SessionState createSession(String sessionId, String studentId,
                                      String examId, String enrollmentId,
                                      long durationSeconds) {
        SessionState state = SessionState.builder()
                .sessionId(sessionId)
                .studentId(studentId)
                .examId(examId)
                .enrollmentId(enrollmentId)
                .answers(new HashMap<>())
                .timeRemainingSecs(durationSeconds)
                .startedAt(Instant.now())
                .lastSavedAt(Instant.now())
                .submitted(false)
                .status("ACTIVE")
                .updatedAt(Instant.now())
                .build();

        // Write to Redis (primary) + MongoDB (durable baseline)
        saveToRedis(state, durationSeconds + 7200);
        mongoRepository.save(state);

        // Increment live counters (per-exam + global for monitoring)
        redisTemplate.opsForValue().increment("exam:live:" + examId + ":active_users");
        redisTemplate.opsForValue().increment(GLOBAL_ACTIVE);
        redisTemplate.opsForValue().increment(GLOBAL_STARTED);

        return state;
    }

    public Optional<SessionState> getSession(String sessionId) {
        // Try Redis first
        SessionState state = getFromRedis(sessionId);
        if (state != null) {
            return Optional.of(state);
        }

        // Redis miss → restore from MongoDB
        log.warn("Redis miss for session {}, loading from MongoDB", sessionId);
        Optional<SessionState> mongoState = mongoRepository.findById(sessionId);
        mongoState.ifPresent(s -> {
            // Re-populate Redis (compute remaining time from startedAt)
            long elapsed = Instant.now().getEpochSecond() - s.getStartedAt().getEpochSecond();
            s.setTimeRemainingSecs(Math.max(0, s.getTimeRemainingSecs() - elapsed));
            saveToRedis(s, s.getTimeRemainingSecs() + 7200);
            log.info("Restored session {} from MongoDB to Redis", sessionId);
        });
        return mongoState;
    }

    // -------------------------------------------------------------------------
    // Answer Saving (hot path — must be < 2ms)
    // -------------------------------------------------------------------------

    public void saveAnswer(String sessionId, String questionId,
                           List<String> answer, boolean markedForReview, long timeSpentSecs) {
        String key = String.format(SESSION_KEY, sessionId);

        // Detect answer change: read existing entry to see if non-empty answer is being replaced
        boolean wasChanged = false;
        try {
            Object existing = redisTemplate.opsForHash().get(key + ":answers", questionId);
            if (existing != null) {
                SessionState.AnswerEntry prev = objectMapper.readValue(
                        existing.toString(), SessionState.AnswerEntry.class);
                boolean hadAnswer = prev.getAnswer() != null && !prev.getAnswer().isEmpty();
                boolean hasNewAnswer = answer != null && !answer.isEmpty();
                // Keep wasChanged sticky: once changed, always changed
                wasChanged = prev.isWasChanged()
                        || (hadAnswer && hasNewAnswer && !prev.getAnswer().equals(answer));
            }
        } catch (Exception ignored) {}

        // Atomic hash field update in Redis — O(1)
        String answerJson;
        try {
            SessionState.AnswerEntry entry = SessionState.AnswerEntry.builder()
                    .answer(answer)
                    .markedForReview(markedForReview)
                    .timeSpentSecs(timeSpentSecs)
                    .wasChanged(wasChanged)
                    .lastUpdated(Instant.now())
                    .build();
            answerJson = objectMapper.writeValueAsString(entry);
        } catch (Exception e) {
            throw new RuntimeException("Answer serialization failed", e);
        }

        redisTemplate.opsForHash().put(key + ":answers", questionId, answerJson);
        redisTemplate.opsForHash().put(key, "lastSavedAt", Instant.now().toString());

        // Live monitoring: bump the per-second answer bucket (used for answers/sec)
        String bucket = ANS_BUCKET + Instant.now().getEpochSecond();
        Long bucketCount = redisTemplate.opsForValue().increment(bucket);
        if (bucketCount != null && bucketCount == 1L) {
            redisTemplate.expire(bucket, Duration.ofSeconds(ANS_BUCKET_TTL));
        }

        // Mark session as dirty for MongoDB flush
        dirtySessions.add(sessionId);

        // Publish to Kafka for monitoring
        kafkaTemplate.send("answer-events", sessionId,
                Map.of("sessionId", sessionId, "questionId", questionId,
                       "hasAnswer", answer != null && !answer.isEmpty(),
                       "timestamp", Instant.now().toString()));
    }

    public void updateTimeRemaining(String sessionId, long timeRemainingSecs) {
        String key = String.format(SESSION_KEY, sessionId);
        redisTemplate.opsForHash().put(key, "timeRemainingSecs", String.valueOf(timeRemainingSecs));
    }

    // -------------------------------------------------------------------------
    // Bulk heartbeat save (from WebSocket heartbeat)
    // -------------------------------------------------------------------------

    public void processBulkHeartbeat(String sessionId, Map<String, Map<String, Object>> answers,
                                     long timeRemainingSecs) {
        String key = String.format(SESSION_KEY, sessionId);
        Map<String, Object> answerHashUpdates = new HashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : answers.entrySet()) {
            try {
                @SuppressWarnings("unchecked")
                List<String> ans = (List<String>) entry.getValue().get("answer");
                SessionState.AnswerEntry ae = SessionState.AnswerEntry.builder()
                        .answer(ans)
                        .markedForReview(Boolean.TRUE.equals(entry.getValue().get("markedForReview")))
                        .timeSpentSecs(((Number) entry.getValue().getOrDefault("timeSpent", 0)).longValue())
                        .lastUpdated(Instant.now())
                        .build();
                answerHashUpdates.put(entry.getKey(), objectMapper.writeValueAsString(ae));
            } catch (Exception ignored) {}
        }

        if (!answerHashUpdates.isEmpty()) {
            redisTemplate.opsForHash().putAll(key + ":answers", answerHashUpdates);
        }
        redisTemplate.opsForHash().put(key, "timeRemainingSecs", String.valueOf(timeRemainingSecs));
        redisTemplate.opsForHash().put(key, "lastSavedAt", Instant.now().toString());
        dirtySessions.add(sessionId);
    }

    // -------------------------------------------------------------------------
    // Submission
    // -------------------------------------------------------------------------

    public void submitSession(String sessionId, String examId) {
        String key = String.format(SESSION_KEY, sessionId);

        // Atomic status update
        redisTemplate.opsForHash().put(key, "status", "SUBMITTED");
        redisTemplate.opsForHash().put(key, "submittedAt", Instant.now().toString());

        // Flush to MongoDB immediately on submission (not wait for scheduled flush)
        SessionState state = buildStateFromRedis(sessionId);
        if (state != null) {
            state.setSubmitted(true);
            state.setSubmittedAt(Instant.now());
            state.setStatus("SUBMITTED");
            mongoRepository.save(state);
            dirtySessions.remove(sessionId);
        }

        // Decrement live counter
        redisTemplate.opsForValue().decrement("exam:live:" + examId + ":active_users");
        redisTemplate.opsForValue().increment("exam:live:" + examId + ":submitted");

        // Publish exam-submitted event for Result Service
        kafkaTemplate.send("exam-events", sessionId,
                Map.of("event", "EXAM_SUBMITTED", "sessionId", sessionId, "examId", examId,
                       "submittedAt", Instant.now().toString()));

        log.info("Session {} submitted for exam {}", sessionId, examId);
    }

    // -------------------------------------------------------------------------
    // Scheduled MongoDB flush (every 15 seconds)
    // -------------------------------------------------------------------------

    @Scheduled(fixedDelay = 15000)
    public void flushDirtySessionsToMongo() {
        if (dirtySessions.isEmpty()) return;

        Set<String> toFlush = new HashSet<>(dirtySessions);
        dirtySessions.removeAll(toFlush);

        int flushed = 0;
        for (String sessionId : toFlush) {
            try {
                SessionState state = buildStateFromRedis(sessionId);
                if (state != null) {
                    state.setUpdatedAt(Instant.now());
                    mongoRepository.save(state);
                    flushed++;
                }
            } catch (Exception e) {
                log.error("Failed to flush session {} to MongoDB: {}", sessionId, e.getMessage());
                dirtySessions.add(sessionId); // re-queue for next flush
            }
        }
        if (flushed > 0) {
            log.debug("Flushed {} sessions to MongoDB", flushed);
        }
    }

    // -------------------------------------------------------------------------
    // Timer enforcement (every 1 second)
    // -------------------------------------------------------------------------

    @Scheduled(fixedRate = 60000) // Check every minute for timed-out sessions
    public void enforceTimeouts() {
        // In production, use a Redis sorted set with expiry scores for efficiency
        // This is a simplified implementation
        log.debug("Running timeout enforcement check");
    }

    // -------------------------------------------------------------------------
    // Live monitoring (super-admin dashboard)
    // -------------------------------------------------------------------------

    /** Aggregate, cluster-wide live metrics read from Redis counters. */
    public Map<String, Object> getLiveMetrics() {
        long active    = num(redisTemplate.opsForValue().get(GLOBAL_ACTIVE));
        long started   = num(redisTemplate.opsForValue().get(GLOBAL_STARTED));
        long submitted = num(redisTemplate.opsForValue().get(GLOBAL_SUBMITTED));

        // answers/sec: average over the last 5 *complete* seconds
        long now = Instant.now().getEpochSecond();
        long sum = 0;
        for (long s = now - 5; s < now; s++) {
            sum += num(redisTemplate.opsForValue().get(ANS_BUCKET + s));
        }
        long answersPerSec = sum / 5;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activeSessions", Math.max(0, active));
        m.put("startedTotal",   started);
        m.put("submittedTotal", submitted);
        m.put("answersPerSec",  answersPerSec);
        m.put("sampledAt",      Instant.now().toString());
        return m;
    }

    private long num(Object v) {
        if (v == null) return 0;
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return 0; }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private SessionState getFromRedis(String sessionId) {
        try {
            String key = String.format(SESSION_KEY, sessionId);
            Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
            if (hash.isEmpty()) return null;
            return buildFromHash(sessionId, hash);
        } catch (Exception e) {
            log.error("Redis read error for session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    private SessionState buildStateFromRedis(String sessionId) {
        String key = String.format(SESSION_KEY, sessionId);
        Map<Object, Object> stateHash = redisTemplate.opsForHash().entries(key);
        Map<Object, Object> answersHash = redisTemplate.opsForHash().entries(key + ":answers");

        if (stateHash.isEmpty()) return null;

        SessionState state = buildFromHash(sessionId, stateHash);

        // Merge answers
        Map<String, SessionState.AnswerEntry> answers = new HashMap<>();
        for (Map.Entry<Object, Object> entry : answersHash.entrySet()) {
            try {
                SessionState.AnswerEntry ae = objectMapper.readValue(
                        entry.getValue().toString(), SessionState.AnswerEntry.class);
                answers.put(entry.getKey().toString(), ae);
            } catch (Exception ignored) {}
        }
        state.setAnswers(answers);
        return state;
    }

    private SessionState buildFromHash(String sessionId, Map<Object, Object> hash) {
        return SessionState.builder()
                .sessionId(sessionId)
                .studentId(str(hash, "studentId"))
                .examId(str(hash, "examId"))
                .enrollmentId(str(hash, "enrollmentId"))
                .timeRemainingSecs(longVal(hash, "timeRemainingSecs"))
                .status(str(hash, "status"))
                .submitted("SUBMITTED".equals(str(hash, "status")))
                .build();
    }

    private void saveToRedis(SessionState state, long ttlSeconds) {
        String key = String.format(SESSION_KEY, state.getSessionId());
        Map<String, Object> hash = new HashMap<>();
        hash.put("studentId", state.getStudentId());
        hash.put("examId", state.getExamId());
        hash.put("enrollmentId", state.getEnrollmentId());
        hash.put("timeRemainingSecs", state.getTimeRemainingSecs());
        hash.put("status", state.getStatus());
        hash.put("startedAt", state.getStartedAt().toString());
        hash.put("lastSavedAt", Instant.now().toString());

        redisTemplate.opsForHash().putAll(key, hash);
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
    }

    private String str(Map<Object, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private long longVal(Map<Object, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return 0;
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0; }
    }
}
