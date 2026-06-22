package com.examplatform.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data @Builder
public class AccessCheckResponse {
    private boolean hasAccess;
    private Instant expiresAt;
}
