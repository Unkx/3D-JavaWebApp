package com.printplatform.repository;

import com.printplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByFacebookId(String facebookId);
    Optional<User> findByGoogleId(String googleId);
}