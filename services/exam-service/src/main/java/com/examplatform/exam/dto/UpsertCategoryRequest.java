package com.examplatform.exam.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UpsertCategoryRequest {

    /** Machine code, e.g. JEE_MAIN. Required on create, ignored on update. */
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$",
             message = "code must be UPPER_SNAKE_CASE (e.g. JEE_MAIN)")
    private String code;

    @NotBlank @Size(max = 120)
    private String title;

    @Size(max = 80)
    private String tag;

    @Pattern(regexp = "^#([0-9a-fA-F]{6})$", message = "color must be a hex like #1a8fe3")
    private String color = "#0f3460";

    @Size(max = 500)
    private String description;

    private int displayOrder = 100;

    private boolean active = true;
}
