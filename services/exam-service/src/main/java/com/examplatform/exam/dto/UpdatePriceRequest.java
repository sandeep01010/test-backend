package com.examplatform.exam.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdatePriceRequest {
    @Min(0)
    private long priceInPaise;
}
