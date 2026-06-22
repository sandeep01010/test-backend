package com.examplatform.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateScopedOrderRequest {
    @NotBlank
    @Pattern(regexp = "^(CATEGORY|GROUP)$", message = "scopeType must be CATEGORY or GROUP")
    private String scopeType;

    @NotBlank
    private String scopeCode;
}
