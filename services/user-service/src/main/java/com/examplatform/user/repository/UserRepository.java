package com.examplatform.user.repository;

import com.examplatform.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    List<User> findByRoleOrderByCreatedAtDesc(User.Role role);

    @Query("""
        SELECT u FROM User u
        WHERE (:roleName IS NULL OR CAST(u.role AS string) = :roleName)
          AND (:search IS NULL OR :search = ''
               OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY u.createdAt DESC
        """)
    Page<User> search(@Param("roleName") String roleName, @Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role")
    long countByRole(User.Role role);

    @Modifying
    @Query("UPDATE User u SET u.verified = true WHERE u.id = :id")
    void markVerified(UUID id);

    @Modifying
    @Query("UPDATE User u SET u.active = :active WHERE u.id = :id")
    void setActive(UUID id, boolean active);

    @Modifying
    @Query("UPDATE User u SET u.role = :role WHERE u.id = :id")
    void setRole(UUID id, User.Role role);
}
