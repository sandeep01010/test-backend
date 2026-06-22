package com.examplatform.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data @Builder
public class CreateScopedOrderResponse {
    private String orderId;
    private String gatewayOrderId;   // null when freeGrant=true (no gateway order needed)
    private BigDecimal amount;       // paise
    private String currency;
    private String scopeType;
    private String scopeCode;
    private Instant expiresAt;       // order expiry (15 min), not access expiry
    private String keyId;
    /** True when the price was 0 — access was granted immediately, no payment step needed. */
    private boolean freeGrant;
}
