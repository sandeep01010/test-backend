package com.examplatform.testengine.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Persisted in MongoDB as durable backup.
 * Live state lives in Redis; this is the fallback on Redis miss.
 */
@Document(collection = "answer_snapshots")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SessionState implements Serializable {

    @Id
    @Indexed(unique = true)
    @Field("session_id")
    private String sessionId;

    @Indexed
    @Field("student_id")
    private String studentId;

    @Indexed
    @Field("exam_id")
    private String examId;

    @Field("enrollment_id")
    private String enrollmentId;

    /** question_id → AnswerEntry */
    private Map<String, AnswerEntry> answers = new HashMap<>();

    @Field("time_remaining_secs")
    private long timeRemainingSecs;

    @Field("started_at")
    private Instant startedAt;

    @Field("last_saved_at")
    private Instant lastSavedAt;

    @Field("is_submitted")
    private boolean submitted;

    @Field("submitted_at")
    private Instant submittedAt;

    @Field("status")
    private String status; // ACTIVE, SUBMITTED, TIMED_OUT

    @Field("updated_at")
    private Instant updatedAt;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AnswerEntry implements Serializable {
        private String answer;              // selected option ID or numerical value
        private boolean markedForReview;
        private long timeSpentSecs;
        private Instant lastUpdated;
    }
}
