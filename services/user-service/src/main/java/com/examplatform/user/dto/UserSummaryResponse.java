package com.examplatform.user.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class UserSummaryResponse {
    private UUID    id;
    private String  firstName;
    private String  lastName;
    private String  email;
    private String  phone;
    private String  role;
    private boolean verified;
    private boolean active;
    private Instant createdAt;
}
