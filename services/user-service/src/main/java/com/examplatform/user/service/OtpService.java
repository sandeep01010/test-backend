package com.examplatform.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redisTemplate;

    @Value("${otp.ttl-seconds:300}")
    private int otpTtlSeconds;

    @Value("${otp.length:6}")
    private int otpLength;

    @Value("${otp.max-attempts:3}")
    private int maxAttempts;

    @Value("${otp.lockout-seconds:1800}")
    private int lockoutSeconds;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String OTP_KEY     = "auth:otp:%s:%s";      // phone:purpose
    private static final String ATTEMPT_KEY = "auth:otp:attempts:%s:%s";
    private static final String LOCKOUT_KEY = "auth:otp:lockout:%s";

    public String generateAndSendOtp(String phone, String purpose) {
        // Check lockout
        if (Boolean.TRUE.equals(redisTemplate.hasKey(String.format(LOCKOUT_KEY, phone)))) {
            throw new RuntimeException("Too many attempts. Please try again after 30 minutes.");
        }

        String otp = generateOtp();
        String key = String.format(OTP_KEY, phone, purpose);

        redisTemplate.opsForValue().set(key, otp, Duration.ofSeconds(otpTtlSeconds));

        // Reset attempt counter on new OTP
        String attemptKey = String.format(ATTEMPT_KEY, phone, purpose);
        redisTemplate.delete(attemptKey);

        // TODO: Integrate SMS gateway (Twilio / MSG91)
        log.info("OTP for {} [{}]: {} (replace with SMS in production)", phone, purpose, otp);
        return otp; // Return for testing; remove in production
    }

    public boolean verifyOtp(String phone, String purpose, String inputOtp) {
        String lockoutKey = String.format(LOCKOUT_KEY, phone);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey))) {
            throw new RuntimeException("Account locked due to too many failed attempts.");
        }

        String key = String.format(OTP_KEY, phone, purpose);
        String storedOtp = redisTemplate.opsForValue().get(key);

        if (storedOtp == null) {
            throw new RuntimeException("OTP expired or not found. Please request a new one.");
        }

        String attemptKey = String.format(ATTEMPT_KEY, phone, purpose);

        if (!storedOtp.equals(inputOtp)) {
            long attempts = redisTemplate.opsForValue().increment(attemptKey);
            redisTemplate.expire(attemptKey, Duration.ofSeconds(otpTtlSeconds));

            if (attempts >= maxAttempts) {
                redisTemplate.opsForValue().set(lockoutKey, "1", Duration.ofSeconds(lockoutSeconds));
                redisTemplate.delete(key);
                throw new RuntimeException("Too many failed attempts. Account locked for 30 minutes.");
            }
            return false;
        }

        // OTP matched — clean up
        redisTemplate.delete(key);
        redisTemplate.delete(attemptKey);
        return true;
    }

    private String generateOtp() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
