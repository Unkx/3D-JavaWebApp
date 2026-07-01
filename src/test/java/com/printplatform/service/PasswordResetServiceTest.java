package com.printplatform.service;

import com.printplatform.model.PasswordResetToken;
import com.printplatform.model.User;
import com.printplatform.repository.PasswordResetTokenRepository;
import com.printplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(userRepository, tokenRepository, emailService, passwordEncoder);
    }

    private User buildUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPassword("old-encoded");
        return user;
    }

    @Test
    void initReset_existingUser_deletesOldTokensCreatesNewTokenAndSendsEmail() {
        User user = buildUser("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        passwordResetService.initReset("user@example.com");

        verify(tokenRepository).deleteExpired(any(LocalDateTime.class));
        verify(tokenRepository).deleteByUser(user);

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getToken()).isNotNull();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());

        verify(emailService).sendPasswordResetEmail(eq("user@example.com"), eq(saved.getToken()));
    }

    @Test
    void initReset_unknownEmail_onlyDeletesExpiredAndDoesNothingElse() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        passwordResetService.initReset("ghost@example.com");

        verify(tokenRepository).deleteExpired(any(LocalDateTime.class));
        verify(tokenRepository, never()).deleteByUser(any());
        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void initReset_emailSendingFails_doesNotPropagateException() {
        User user = buildUser("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        doThrow(new MailSendException("smtp down"))
                .when(emailService).sendPasswordResetEmail(anyString(), any(UUID.class));

        assertThatCode(() -> passwordResetService.initReset("user@example.com")).doesNotThrowAnyException();

        verify(tokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void confirmReset_validToken_updatesPasswordAndMarksTokenUsed() {
        User user = buildUser("user@example.com");
        UUID tokenUuid = UUID.randomUUID();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setId(UUID.randomUUID());
        prt.setUser(user);
        prt.setToken(tokenUuid);
        prt.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        prt.setUsed(false);

        when(tokenRepository.findByToken(tokenUuid)).thenReturn(Optional.of(prt));
        when(passwordEncoder.encode("newPassword123")).thenReturn("encoded-new-password");

        passwordResetService.confirmReset(tokenUuid.toString(), "newPassword123");

        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        assertThat(prt.isUsed()).isTrue();
        verify(userRepository).save(user);
        verify(tokenRepository).save(prt);
    }

    @Test
    void confirmReset_malformedTokenString_throwsBadRequest() {
        assertThatThrownBy(() -> passwordResetService.confirmReset("not-a-uuid", "newPassword123"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(tokenRepository);
    }

    @Test
    void confirmReset_unknownToken_throwsBadRequest() {
        UUID tokenUuid = UUID.randomUUID();
        when(tokenRepository.findByToken(tokenUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.confirmReset(tokenUuid.toString(), "newPassword123"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void confirmReset_alreadyUsedToken_throwsBadRequest() {
        User user = buildUser("user@example.com");
        UUID tokenUuid = UUID.randomUUID();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(user);
        prt.setToken(tokenUuid);
        prt.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        prt.setUsed(true);

        when(tokenRepository.findByToken(tokenUuid)).thenReturn(Optional.of(prt));

        assertThatThrownBy(() -> passwordResetService.confirmReset(tokenUuid.toString(), "newPassword123"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmReset_expiredToken_throwsBadRequest() {
        User user = buildUser("user@example.com");
        UUID tokenUuid = UUID.randomUUID();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(user);
        prt.setToken(tokenUuid);
        prt.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        prt.setUsed(false);

        when(tokenRepository.findByToken(tokenUuid)).thenReturn(Optional.of(prt));

        assertThatThrownBy(() -> passwordResetService.confirmReset(tokenUuid.toString(), "newPassword123"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(userRepository, never()).save(any());
    }
}
