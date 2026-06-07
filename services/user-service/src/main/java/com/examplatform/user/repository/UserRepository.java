package com.examplatform.user.repository;

import com.examplatform.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    @Query("SELECT u FROM User u WHERE u.email = :identifier OR u.phone = :identifier")
    Optional<User> findByEmailOrPhone(String identifier);

    @Modifying
    @Query("UPDATE User u SET u.verified = true WHERE u.id = :id")
    void markVerified(UUID id);

    @Modifying
    @Query("UPDATE User u SET u.active = :active WHERE u.id = :id")
    void setActive(UUID id, boolean active);
}
