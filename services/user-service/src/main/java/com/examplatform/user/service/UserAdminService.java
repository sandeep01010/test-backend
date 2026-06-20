package com.examplatform.user.service;

import com.examplatform.user.dto.InviteUserRequest;
import com.examplatform.user.dto.UserPageResponse;
import com.examplatform.user.dto.UserSummaryResponse;
import com.examplatform.user.model.User;
import com.examplatform.user.repository.UserRepository;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final EmailService   emailService;

    private static final Argon2       ARGON2       = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    private static final int          ITERATIONS   = 2;
    private static final int          MEMORY_KB    = 4096;
    private static final int          PARALLELISM  = 1;
    private static final String       CHARS        = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#!";
    private static final SecureRandom RANDOM       = new SecureRandom();

    public UserPageResponse listUsers(String roleStr, String search, int page, int size) {
        // Pass null when "ALL" so the JPQL :roleName IS NULL branch matches all rows
        String roleName = (roleStr == null || roleStr.isBlank() || "ALL".equalsIgnoreCase(roleStr))
                ? null : roleStr.toUpperCase();

        Page<User> pg = userRepository.search(roleName, search == null ? "" : search,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        List<UserSummaryResponse> users = pg.getContent().stream()
                .map(this::toSummary).toList();

        return UserPageResponse.builder()
                .users(users)
                .totalElements(pg.getTotalElements())
                .totalPages(pg.getTotalPages())
                .page(page)
                .size(size)
                .totalStudents(userRepository.countByRole(User.Role.STUDENT))
                .totalAdmins(userRepository.countByRole(User.Role.ADMIN))
                .totalSuperAdmins(userRepository.countByRole(User.Role.SUPER_ADMIN))
                .build();
    }

    public UserSummaryResponse getUser(UUID userId) {
        return toSummary(userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId)));
    }

    @Transactional
    public UserSummaryResponse inviteUser(InviteUserRequest req, String callerRole) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + req.getEmail());
        }
        if (req.getPhone() != null && !req.getPhone().isBlank()
                && userRepository.existsByPhone(req.getPhone())) {
            throw new IllegalArgumentException("Phone already registered: " + req.getPhone());
        }

        User.Role targetRole = User.Role.valueOf(req.getRole().toUpperCase());
        // Only SUPER_ADMIN can create ADMIN accounts
        if (targetRole == User.Role.ADMIN && !"SUPER_ADMIN".equals(callerRole)) {
            throw new SecurityException("Only Super Admin can invite Admin users.");
        }

        String tempPassword = generateTempPassword();
        String hash         = ARGON2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, tempPassword.toCharArray());

        User user = User.builder()
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .email(req.getEmail())
                .phone(req.getPhone() != null && !req.getPhone().isBlank() ? req.getPhone() : null)
                .passwordHash(hash)
                .role(targetRole)
                .verified(true)   // admin-created accounts skip OTP
                .active(true)
                .build();

        user = userRepository.save(user);
        log.info("Admin invited user {} ({}) with role {}", user.getEmail(), user.getId(), targetRole);

        emailService.sendInviteEmail(user.getEmail(), user.getFirstName(),
                formatRole(targetRole), tempPassword);

        return toSummary(user);
    }

    @Transactional
    public void setUserStatus(UUID userId, boolean active, String callerRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getRole() == User.Role.SUPER_ADMIN) {
            throw new SecurityException("Cannot modify Super Admin status.");
        }
        userRepository.setActive(userId, active);
        log.info("User {} {} by caller with role {}", userId, active ? "activated" : "deactivated", callerRole);
    }

    @Transactional
    public void changeUserRole(UUID userId, String newRoleStr) {
        User.Role newRole = User.Role.valueOf(newRoleStr.toUpperCase());
        if (newRole == User.Role.SUPER_ADMIN) {
            throw new SecurityException("Cannot promote to Super Admin via this endpoint.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getRole() == User.Role.SUPER_ADMIN) {
            throw new SecurityException("Cannot change Super Admin role.");
        }
        userRepository.setRole(userId, newRole);
        log.info("User {} role changed to {}", userId, newRole);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getRole() == User.Role.SUPER_ADMIN) {
            throw new SecurityException("Cannot delete Super Admin.");
        }
        userRepository.deleteById(userId);
        log.info("User {} deleted", userId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String generateTempPassword() {
        // Always meets: uppercase, lowercase, digit, special char
        String upper   = "ABCDEFGHJKMNPQRSTUVWXYZ";
        String lower   = "abcdefghjkmnpqrstuvwxyz";
        String digits  = "23456789";
        String special = "@#!";
        StringBuilder sb = new StringBuilder();
        sb.append(upper.charAt(RANDOM.nextInt(upper.length())));
        sb.append(lower.charAt(RANDOM.nextInt(lower.length())));
        sb.append(lower.charAt(RANDOM.nextInt(lower.length())));
        sb.append(digits.charAt(RANDOM.nextInt(digits.length())));
        sb.append(digits.charAt(RANDOM.nextInt(digits.length())));
        sb.append(special.charAt(RANDOM.nextInt(special.length())));
        // fill to 10 chars
        for (int i = 6; i < 10; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        // shuffle
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp;
        }
        return new String(chars);
    }

    private String formatRole(User.Role role) {
        return switch (role) {
            case STUDENT    -> "Student";
            case ADMIN      -> "Admin";
            case SUPER_ADMIN -> "Super Admin";
        };
    }

    private UserSummaryResponse toSummary(User u) {
        return UserSummaryResponse.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .role(u.getRole().name())
                .verified(u.isVerified())
                .active(u.isActive())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
