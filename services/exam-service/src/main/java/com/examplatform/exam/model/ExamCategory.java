package com.examplatform.exam.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Data-driven exam category (JEE Main, NEET, CUET, ...).
 *
 * EXTENSIBILITY: a new exam track is added by INSERTING a row here
 * (via the Admin UI -> POST /api/v1/exams/categories) — NO code change,
 * NO redeploy. The student dashboard and filters read categories from this
 * table at request time.
 */
@Entity
@Table(name = "exam_categories")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ExamCategory {

    /** Stable machine code, e.g. "JEE_MAIN". Used as FK from exams.category_code. */
    @Id
    @Column(length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String title;          // "JEE Main"

    @Column(length = 80)
    private String tag;            // "Engineering"

    @Column(length = 20)
    private String color;          // "#1a8fe3" — drives the UI accent

    @Column(length = 500)
    private String description;

    @Column(name = "display_order")
    private int displayOrder = 100;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
