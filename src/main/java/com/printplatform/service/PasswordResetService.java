package com.printplatform.service;

import com.printplatform.model.PasswordResetToken;
import com.printplatform.model.User;
import com.printplatform.repository.PasswordResetTokenRepository;
import com.printplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                EmailService emailService,
                                PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void initReset(String email) {
        tokenRepository.deleteExpired(LocalDateTime.now());
        userRepository.findByEmail(email).ifPresent(user -> {
            tokenRepository.deleteByUser(user);
            PasswordResetToken prt = new PasswordResetToken();
            prt.setUser(user);
            prt.setToken(UUID.randomUUID());
            prt.setExpiresAt(LocalDateTime.now().plusHours(1));
            tokenRepository.save(prt);
            try {
                emailService.sendPasswordResetEmail(email, prt.getToken());
            } catch (MailException e) {
                log.warn("Failed to send password reset email to {}: {}", email, e.getMessage());
            }
        });
        // silently no-ops when email not found — prevents enumeration
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        UUID tokenUuid;
        try {
            tokenUuid = UUID.fromString(rawToken);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token wygasł lub jest nieprawidłowy");
        }

        PasswordResetToken prt = tokenRepository.findByToken(tokenUuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Token wygasł lub jest nieprawidłowy"));

        if (prt.isUsed() || prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token wygasł lub jest nieprawidłowy");
        }

        User user = prt.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        prt.setUsed(true);
        tokenRepository.save(prt);
    }
}
