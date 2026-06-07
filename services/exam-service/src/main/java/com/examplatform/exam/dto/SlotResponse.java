package com.examplatform.exam.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data @Builder
public class SlotResponse {
    private UUID id;
    private String slotDate;
    private String startTime;
    private String endTime;
    private int capacity;
    private int enrolledCount;
    private int available;
    private String centerName;
    private String city;
    private String state;
    private boolean full;
}
