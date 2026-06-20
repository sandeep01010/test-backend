package com.examplatform.user.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class UserPageResponse {
    private List<UserSummaryResponse> users;
    private long   totalElements;
    private int    totalPages;
    private int    page;
    private int    size;
    private long   totalStudents;
    private long   totalAdmins;
    private long   totalSuperAdmins;
}
