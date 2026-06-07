package com.examplatform.result.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "results")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "enrollment_id", unique = true)
    private UUID enrollmentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(name = "total_score")
    private Double totalScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "section_scores", columnDefinition = "jsonb")
    private Map<String, Double> sectionScores;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "wrong_count")
    private Integer wrongCount;

    @Column(name = "skipped_count")
    private Integer skippedCount;

    @Column(name = "time_taken_secs")
    private Integer timeTakenSecs;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "percentile")
    private Double percentile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultStatus status;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum ResultStatus {
        PENDING, EVALUATED, PUBLISHED, WITHHELD
    }
}
