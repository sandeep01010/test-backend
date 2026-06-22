package com.examplatform.exam.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A combined category group (e.g. "JEE Mains + Advanced") that aggregates papers from
 * several {@link ExamCategory} codes into one browsable view. A category can belong to
 * any number of groups — membership here is purely additive and never changes an exam's
 * own category_code, so existing attempts/enrollments keep working unmodified.
 */
@Entity
@Table(name = "category_groups")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CategoryGroup {

    /** Stable machine code, e.g. "JEE_MAIN_ADVANCED". */
    @Id
    @Column(length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String title;          // "JEE Mains + Advanced"

    @Column(length = 80)
    private String tag;

    @Column(length = 20)
    private String color;

    @Column(length = 500)
    private String description;

    @Column(name = "display_order")
    private int displayOrder = 100;

    @Column(nullable = false)
    private boolean active = true;

    /** Bundle price in paise for 1 year of access across all member categories' locked
     *  exams. Independent of the member categories' own individual prices. 0 = free. */
    @Column(name = "price_in_paise")
    private long priceInPaise = 0;

    /** Member category codes — many-to-many via the category_group_members join table. */
    @ElementCollection
    @CollectionTable(name = "category_group_members", joinColumns = @JoinColumn(name = "group_code"))
    @Column(name = "category_code")
    @Builder.Default
    private Set<String> memberCodes = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
