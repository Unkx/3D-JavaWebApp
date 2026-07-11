package com.printplatform.repository;

import com.printplatform.model.EmailVerificationToken;
import com.printplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByToken(UUID token);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmailVerificationToken t WHERE t.user = :user")
    void deleteByUser(User user);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);
}
