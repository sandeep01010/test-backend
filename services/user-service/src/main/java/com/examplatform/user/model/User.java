package com.examplatform.user.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(unique = true, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "aadhaar_hash")
    private String aadhaarHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "is_verified")
    private boolean verified = false;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "is_subscribed")
    private boolean subscribed = false;

    @Column(name = "subscribed_at")
    private Instant subscribedAt;

    @Column(name = "subscribed_until")
    private Instant subscribedUntil;

    @Column(name = "subscription_plan", length = 50)
    private String subscriptionPlan;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum Role {
        STUDENT, ADMIN, SUPER_ADMIN
    }
}
