package com.examplatform.user.service;

import com.examplatform.user.dto.*;
import com.examplatform.user.model.User;
import com.examplatform.user.repository.UserRepository;
import com.examplatform.user.security.JwtService;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final Argon2 ARGON2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    // Argon2id params — reduced for dev; tune up for production
    private static final int ITERATIONS   = 2;
    private static final int MEMORY_KB    = 4096;
    private static final int PARALLELISM  = 1;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        if (req.getPhone() != null && userRepository.existsByPhone(req.getPhone())) {
            throw new IllegalArgumentException("Phone already registered");
        }

        String passwordHash = ARGON2.hash(ITERATIONS, MEMORY_KB, PARALLELISM,
                req.getPassword().toCharArray());

        User user = User.builder()
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .passwordHash(passwordHash)
                .role(User.Role.STUDENT)
                .verified(false)
                .active(true)
                .build();

        user = userRepository.save(user);

        // Publish user registered event
        kafkaTemplate.send("user-events", user.getId().toString(),
                Map.of("event", "USER_REGISTERED", "userId", user.getId(), "email", user.getEmail()));

        // Send verification OTP
        if (user.getPhone() != null) {
            otpService.generateAndSendOtp(user.getPhone(), "REGISTRATION");
        }

        log.info("User registered: {}", user.getId());

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmailOrPhone(req.getIdentifier())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!user.isActive()) {
            throw new RuntimeException("Account is deactivated. Contact support.");
        }

        if (!ARGON2.verify(user.getPasswordHash(), req.getPassword().toCharArray())) {
            throw new RuntimeException("Invalid credentials");
        }

        log.info("User logged in: {}", user.getId());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        Claims claims = jwtService.validateToken(refreshToken);
        if (!"REFRESH".equals(claims.get("type"))) {
            throw new RuntimeException("Not a refresh token");
        }

        UUID userId = UUID.fromString(claims.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isActive()) {
            throw new RuntimeException("Account deactivated");
        }

        // Blacklist old refresh token
        jwtService.blacklistToken(refreshToken);

        return buildAuthResponse(user);
    }

    @Transactional
    public void verifyOtp(String phone, String otp) {
        boolean verified = otpService.verifyOtp(phone, "REGISTRATION", otp);
        if (!verified) {
            throw new RuntimeException("Invalid OTP");
        }

        userRepository.findByPhone(phone).ifPresent(user -> {
            userRepository.markVerified(user.getId());
            log.info("User verified: {}", user.getId());
        });
    }

    public void logout(String accessToken) {
        jwtService.blacklistToken(accessToken);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .verified(user.isVerified())
                .build();
    }
}
