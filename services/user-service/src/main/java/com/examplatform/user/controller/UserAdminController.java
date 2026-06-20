package com.examplatform.user.controller;

import com.examplatform.user.dto.InviteUserRequest;
import com.examplatform.user.dto.UserPageResponse;
import com.examplatform.user.dto.UserSummaryResponse;
import com.examplatform.user.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin-only user management endpoints.
 * All requests must come through the API Gateway which injects X-User-Role.
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService adminService;

    /** GET /admin/users?role=STUDENT&search=&page=0&size=20 */
    @GetMapping
    public ResponseEntity<UserPageResponse> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = "userRole", required = false) String callerRole) {

        requireAdminOrSuperAdmin(callerRole);
        return ResponseEntity.ok(adminService.listUsers(role, search, page, size));
    }

    /** GET /admin/users/{userId} */
    @GetMapping("/{userId}")
    public ResponseEntity<UserSummaryResponse> getUser(
            @PathVariable UUID userId,
            @RequestAttribute(value = "userRole", required = false) String callerRole) {
        requireAdminOrSuperAdmin(callerRole);
        return ResponseEntity.ok(adminService.getUser(userId));
    }

    /** POST /admin/users/invite — create + send invite email */
    @PostMapping("/invite")
    public ResponseEntity<UserSummaryResponse> invite(
            @Valid @RequestBody InviteUserRequest req,
            @RequestAttribute(value = "userRole", required = false) String callerRole) {
        requireAdminOrSuperAdmin(callerRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.inviteUser(req, callerRole));
    }

    /** PATCH /admin/users/{userId}/status  body: { "active": true|false } */
    @PatchMapping("/{userId}/status")
    public ResponseEntity<Map<String, String>> setStatus(
            @PathVariable UUID userId,
            @RequestBody Map<String, Boolean> body,
            @RequestAttribute(value = "userRole", required = false) String callerRole) {
        requireAdminOrSuperAdmin(callerRole);
        boolean active = Boolean.TRUE.equals(body.get("active"));
        adminService.setUserStatus(userId, active, callerRole);
        return ResponseEntity.ok(Map.of("status", active ? "activated" : "deactivated"));
    }

    /** PATCH /admin/users/{userId}/role  body: { "role": "ADMIN" }  — super admin only */
    @PatchMapping("/{userId}/role")
    public ResponseEntity<Map<String, String>> changeRole(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> body,
            @RequestAttribute(value = "userRole", required = false) String callerRole) {
        requireSuperAdmin(callerRole);
        adminService.changeUserRole(userId, body.get("role"));
        return ResponseEntity.ok(Map.of("role", body.get("role")));
    }

    /** DELETE /admin/users/{userId} — super admin only */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID userId,
            @RequestAttribute(value = "userRole", required = false) String callerRole) {
        requireSuperAdmin(callerRole);
        adminService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    private void requireAdminOrSuperAdmin(String role) {
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new SecurityException("Access denied.");
        }
    }

    private void requireSuperAdmin(String role) {
        if (!"SUPER_ADMIN".equals(role)) {
            throw new SecurityException("Super Admin access required.");
        }
    }
}
