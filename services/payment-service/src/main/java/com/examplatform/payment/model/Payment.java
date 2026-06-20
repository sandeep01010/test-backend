package com.examplatform.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_user_id", columnList = "user_id"),
        @Index(name = "idx_payment_order_id", columnList = "order_id"),
        @Index(name = "idx_payment_gateway_order_id", columnList = "gateway_order_id"),
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    public enum PaymentStatus {
        CREATED, PENDING, SUCCESS, FAILED, REFUNDED, EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", unique = true, nullable = false, length = 64)
    private String orderId;

    @Column(name = "gateway_order_id", length = 128)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 128)
    private String gatewayPaymentId;

    @Column(name = "gateway_signature", length = 256)
    private String gatewaySignature;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 8)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "plan", length = 64)
    private String plan;

    @Column(name = "duration_days")
    private int durationDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(name = "payment_method", length = 32)
    private String paymentMethod;

    @Column(name = "upi_id", length = 64)
    private String upiId;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "refund_id", length = 128)
    private String refundId;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
