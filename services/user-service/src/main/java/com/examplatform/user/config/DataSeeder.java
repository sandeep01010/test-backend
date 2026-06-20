package com.examplatform.user.config;

import com.examplatform.user.model.User;
import com.examplatform.user.repository.UserRepository;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * DataSeeder — runs once on every startup.
 *
 * Creates default admin accounts if they don't already exist.
 * Uses the same Argon2id hasher as UserService — passwords are always correct.
 *
 * Default credentials (CHANGE IN PRODUCTION):
 *   admin@examforge.com  /  Admin@1234    → role: ADMIN
 *   super@examforge.com  /  Super@1234    → role: SUPER_ADMIN
 *
 * Safe to restart: skips users that already exist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    // Exactly the same Argon2id params as UserService
    private static final Argon2 ARGON2      = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    private static final int    ITERATIONS  = 2;
    private static final int    MEMORY_KB   = 4096;
    private static final int    PARALLELISM = 1;

    @Override
    public void run(String... args) {
        seedUser(
            "admin@examforge.com", "9000000001",
            "Admin", "User",
            "Admin@1234",
            User.Role.ADMIN
        );
        seedUser(
            "super@examforge.com", "9000000002",
            "Super", "Admin",
            "Super@1234",
            User.Role.SUPER_ADMIN
        );
    }

    private void seedUser(
        String email, String phone,
        String firstName, String lastName,
        String rawPassword,
        User.Role role
    ) {
        if (userRepository.existsByEmail(email)) {
            log.debug("[DataSeeder] {} already exists — skipping", email);
            return;
        }

        String hash = ARGON2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, rawPassword.toCharArray());

        User user = User.builder()
            .email(email)
            .phone(phone)
            .firstName(firstName)
            .lastName(lastName)
            .passwordHash(hash)
            .role(role)
            .verified(true)   // pre-verified — no OTP needed
            .active(true)
            .build();

        userRepository.save(user);
        log.info("[DataSeeder] ✅ Seeded {} → {}", role, email);
    }
}
