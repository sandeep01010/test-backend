package com.examplatform.exam.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data @Builder
public class EnrollmentResponse {
    private UUID enrollmentId;
    private String rollNumber;
    private String slotDate;
    private String startTime;
    private String centerName;
}
