package com.examplatform.exam.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "exams")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String title;

    private String description;

    @Column(name = "exam_type", nullable = false, length = 50)
    private String examType;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "duration_mins", nullable = false)
    private int durationMins;

    @Column(name = "total_marks", nullable = false)
    private int totalMarks;

    @Column(name = "negative_marks")
    private double negativeMarks;

    @Column(name = "passing_marks")
    private Double passingMarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamStatus status;

    private String instructions;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "shuffle_questions")
    private boolean shuffleQuestions = true;

    @Column(name = "show_result_immediately")
    private boolean showResultImmediately = false;

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ExamSection> sections = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum ExamStatus {
        DRAFT, PUBLISHED, LIVE, COMPLETED, CANCELLED
    }
}
