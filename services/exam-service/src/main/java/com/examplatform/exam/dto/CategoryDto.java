package com.examplatform.exam.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class CategoryDto {
    private String code;
    private String title;
    private String tag;
    private String color;
    private String description;
    private int displayOrder;
    private boolean active;
}
