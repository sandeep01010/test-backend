package com.examplatform.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {

    private String orderId;
    private String gatewayOrderId;
    private BigDecimal amount;
    private String currency;
    private String plan;
    private int durationDays;
    private Instant expiresAt;
    private String keyId;
}
