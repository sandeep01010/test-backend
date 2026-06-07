package com.examplatform.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email or phone is required")
    private String identifier;  // accepts email or phone

    @NotBlank(message = "Password is required")
    private String password;
}
