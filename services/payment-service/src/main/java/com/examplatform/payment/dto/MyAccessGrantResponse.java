package com.examplatform.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data @Builder
public class MyAccessGrantResponse {
    private String scopeType;
    private String scopeCode;
    private Instant purchasedAt;
    private Instant expiresAt;
}
