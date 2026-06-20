package com.examplatform.user.controller;

import com.examplatform.user.dto.*;
import com.examplatform.user.service.OtpService;
import com.examplatform.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final OtpService otpService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(userService.login(req));
    }

    @PostMapping("/otp/send")
    public ResponseEntity<Map<String, String>> sendOtp(@Valid @RequestBody OtpRequest req) {
        otpService.generateAndSendOtp(req.getPhone(), req.getPurpose());
        return ResponseEntity.ok(Map.of("message", "OTP sent successfully"));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<Map<String, String>> verifyOtp(
            @RequestParam String phone,
            @RequestParam String otp) {
        userService.verifyOtp(phone, otp);
        return ResponseEntity.ok(Map.of("message", "Verification successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestHeader("X-Refresh-Token") String refreshToken) {
        return ResponseEntity.ok(userService.refreshToken(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            userService.logout(authHeader.substring(7));
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /** POST /auth/subscribe — activate subscription after payment confirmation */
    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(
            @RequestAttribute("userId") UUID userId,
            @RequestBody Map<String, String> body) {
        String plan        = body.getOrDefault("plan", "PRO_ANNUAL");
        int    durationDays = Integer.parseInt(body.getOrDefault("durationDays", "365"));
        userService.activateSubscription(userId, plan, durationDays);
        return ResponseEntity.ok(Map.of("status", "SUBSCRIBED", "plan", plan));
    }

    /** GET /auth/subscription-status — check current user's subscription */
    @GetMapping("/subscription-status")
    public ResponseEntity<Map<String, Object>> subscriptionStatus(
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(userService.getSubscriptionStatus(userId));
    }
}
