package com.printplatform.service;

import com.printplatform.model.EmailVerificationToken;
import com.printplatform.model.User;
import com.printplatform.repository.EmailVerificationTokenRepository;
import com.printplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;

    public EmailVerificationService(UserRepository userRepository,
                                    EmailVerificationTokenRepository tokenRepository,
                                    EmailService emailService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
    }

    @Transactional
    public void issueAndSendToken(User user) {
        tokenRepository.deleteByUser(user);
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setUser(user);
        evt.setToken(UUID.randomUUID());
        evt.setExpiresAt(LocalDateTime.now().plusHours(24));
        tokenRepository.save(evt);
        try {
            emailService.sendVerificationEmail(user.getEmail(), evt.getToken());
        } catch (MailException e) {
            log.warn("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    @Transactional
    public void verify(String rawToken) {
        UUID tokenUuid;
        try {
            tokenUuid = UUID.fromString(rawToken);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link wygasł lub jest nieprawidłowy.");
        }

        EmailVerificationToken evt = tokenRepository.findByToken(tokenUuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Link wygasł lub jest nieprawidłowy."));

        if (evt.isUsed() || evt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link wygasł lub jest nieprawidłowy.");
        }

        User user = evt.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        evt.setUsed(true);
        tokenRepository.save(evt);
    }

    @Transactional
    public void resend(String email) {
        tokenRepository.deleteExpired(LocalDateTime.now());
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::issueAndSendToken);
        // silently no-ops when email not found or already verified — prevents enumeration
    }
}
