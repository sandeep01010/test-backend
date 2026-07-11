package com.examplatform.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class CategoryGroupDto {
    private String code;
    private String title;
    private String tag;
    private String color;
    private String description;
    private int displayOrder;
    private boolean active;
    private List<String> memberCodes;
    private long priceInPaise;
}
