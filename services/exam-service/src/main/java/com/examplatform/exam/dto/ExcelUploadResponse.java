package com.examplatform.exam.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class ExcelUploadResponse {
    private UUID   examId;
    private String title;
    private String categoryCode;
    private String testType;
    private int    questionsImported;
    private String status;   // PUBLISHED
    private String message;
}
