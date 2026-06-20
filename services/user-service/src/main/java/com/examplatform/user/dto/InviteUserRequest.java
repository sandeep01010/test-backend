package com.examplatform.user.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class InviteUserRequest {

    @NotBlank @Size(min = 2, max = 100)
    private String firstName;

    @NotBlank @Size(min = 2, max = 100)
    private String lastName;

    @NotBlank @Email
    private String email;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian phone number")
    private String phone;

    @NotBlank @Pattern(regexp = "STUDENT|ADMIN", message = "Role must be STUDENT or ADMIN")
    private String role;
}
