package com.examplatform.user.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;       // seconds
    private UUID userId;
    private String email;
    private String role;
    private boolean verified;
}
