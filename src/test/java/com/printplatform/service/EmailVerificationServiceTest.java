package com.printplatform.service;

import com.printplatform.model.EmailVerificationToken;
import com.printplatform.model.User;
import com.printplatform.repository.EmailVerificationTokenRepository;
import com.printplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
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
class EmailVerificationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailVerificationTokenRepository tokenRepository;
    @Mock
    private EmailService emailService;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(userRepository, tokenRepository, emailService);
    }

    private User buildUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setEmailVerified(false);
        return user;
    }

    @Test
    void issueAndSendToken_deletesOldTokensCreatesNewTokenExpiring24hAndSendsEmail() {
        User user = buildUser("user@example.com");

        service.issueAndSendToken(user);

        verify(tokenRepository).deleteByUser(user);

        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(captor.capture());
        EmailVerificationToken saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getToken()).isNotNull();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(23));
        assertThat(saved.getExpiresAt()).isBefore(LocalDateTime.now().plusHours(25));

        verify(emailService).sendVerificationEmail(eq("user@example.com"), eq(saved.getToken()));
    }

    @Test
    void issueAndSendToken_emailSendingFails_doesNotPropagateException() {
        User user = buildUser("user@example.com");
        doThrow(new MailSendException("smtp down"))
                .when(emailService).sendVerificationEmail(anyString(), any(UUID.class));

        assertThatCode(() -> service.issueAndSendToken(user)).doesNotThrowAnyException();

        verify(tokenRepository).save(any(EmailVerificationToken.class));
    }

    @Test
    void verify_validToken_marksUserVerifiedAndTokenUsed() {
        User user = buildUser("user@example.com");
        UUID tokenUuid = UUID.randomUUID();
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setUser(user);
        evt.setToken(tokenUuid);
        evt.setExpiresAt(LocalDateTime.now().plusHours(23));
        evt.setUsed(false);

        when(tokenRepository.findByToken(tokenUuid)).thenReturn(Optional.of(evt));

        service.verify(tokenUuid.toString());

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(evt.isUsed()).isTrue();
        verify(userRepository).save(user);
        verify(tokenRepository).save(evt);
    }

    @Test
    void verify_malformedTokenString_throwsBadRequest() {
        assertThatThrownBy(() -> service.verify("not-a-uuid"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(tokenRepository);
    }

    @Test
    void verify_unknownToken_throwsBadRequest() {
        UUID tokenUuid = UUID.randomUUID();
        when(tokenRepository.findByToken(tokenUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(tokenUuid.toString()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void verify_expiredToken_throwsBadRequest() {
        User user = buildUser("user@example.com");
        UUID tokenUuid = UUID.randomUUID();
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setUser(user);
        evt.setToken(tokenUuid);
        evt.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        evt.setUsed(false);

        when(tokenRepository.findByToken(tokenUuid)).thenReturn(Optional.of(evt));

        assertThatThrownBy(() -> service.verify(tokenUuid.toString()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verify_alreadyUsedToken_throwsBadRequest() {
        User user = buildUser("user@example.com");
        UUID tokenUuid = UUID.randomUUID();
        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setUser(user);
        evt.setToken(tokenUuid);
        evt.setExpiresAt(LocalDateTime.now().plusHours(1));
        evt.setUsed(true);

        when(tokenRepository.findByToken(tokenUuid)).thenReturn(Optional.of(evt));

        assertThatThrownBy(() -> service.verify(tokenUuid.toString()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void resend_unverifiedUser_issuesNewTokenAndSendsEmail() {
        User user = buildUser("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        service.resend("user@example.com");

        verify(tokenRepository).deleteExpired(any(LocalDateTime.class));
        verify(tokenRepository).deleteByUser(user);
        verify(tokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).sendVerificationEmail(eq("user@example.com"), any(UUID.class));
    }

    @Test
    void resend_alreadyVerifiedUser_doesNothing() {
        User user = buildUser("user@example.com");
        user.setEmailVerified(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        service.resend("user@example.com");

        verify(tokenRepository).deleteExpired(any(LocalDateTime.class));
        verify(tokenRepository, never()).deleteByUser(any());
        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void resend_unknownEmail_onlyDeletesExpiredAndDoesNothingElse() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.resend("ghost@example.com");

        verify(tokenRepository).deleteExpired(any(LocalDateTime.class));
        verify(tokenRepository, never()).deleteByUser(any());
        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }
}
