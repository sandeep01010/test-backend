package com.examplatform.payment.repository;

import com.examplatform.payment.model.AccessGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccessGrantRepository extends JpaRepository<AccessGrant, UUID> {

    @Query("""
           SELECT g FROM AccessGrant g
           WHERE g.userId = :userId AND g.scopeType = :scopeType AND g.scopeCode = :scopeCode
             AND g.expiresAt > :now
           ORDER BY g.expiresAt DESC
           """)
    List<AccessGrant> findActive(UUID userId, AccessGrant.ScopeType scopeType, String scopeCode, Instant now);

    @Query("""
           SELECT g FROM AccessGrant g
           WHERE g.userId = :userId AND g.expiresAt > :now
           ORDER BY g.expiresAt DESC
           """)
    List<AccessGrant> findAllActiveForUser(UUID userId, Instant now);
}
