package com.examplatform.payment.dto;

import com.examplatform.payment.model.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID id;
    private String orderId;
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private BigDecimal amount;
    private String currency;
    private String plan;
    private int durationDays;
    private Payment.PaymentStatus status;
    private String paymentMethod;
    private String upiId;
    private String failureReason;
    private String refundId;
    private Instant refundedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .gatewayOrderId(payment.getGatewayOrderId())
                .gatewayPaymentId(payment.getGatewayPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .plan(payment.getPlan())
                .durationDays(payment.getDurationDays())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .upiId(payment.getUpiId())
                .failureReason(payment.getFailureReason())
                .refundId(payment.getRefundId())
                .refundedAt(payment.getRefundedAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .expiresAt(payment.getExpiresAt())
                .build();
    }
}
