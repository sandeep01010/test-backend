package com.examplatform.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-student, per-category(or group) access grant — created on successful payment for
 * a scoped (category/group) order. Separate from the existing global Pro/Basic subscription
 * on the User entity: a student can hold several of these at once (e.g. JEE_MAIN bought
 * individually AND a JEE_MAIN_ADVANCED group bundle), each with its own 1-year expiry.
 */
@Entity
@Table(
    name = "access_grants",
    indexes = {
        @Index(name = "idx_grant_user_scope", columnList = "user_id, scope_type, scope_code"),
        @Index(name = "idx_grant_expires_at", columnList = "expires_at")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AccessGrant {

    public enum ScopeType { CATEGORY, GROUP }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 16)
    private ScopeType scopeType;

    @Column(name = "scope_code", nullable = false, length = 40)
    private String scopeCode;

    /** The Payment.orderId that created this grant — for traceability/refund lookups. */
    @Column(name = "order_id", length = 64)
    private String orderId;

    @CreationTimestamp
    @Column(name = "purchased_at", nullable = false, updatable = false)
    private Instant purchasedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
