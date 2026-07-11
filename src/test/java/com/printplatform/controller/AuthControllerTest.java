package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.ForgotPasswordRequest;
import com.printplatform.dto.LoginRequest;
import com.printplatform.dto.RegisterRequest;
import com.printplatform.dto.ResendVerificationRequest;
import com.printplatform.dto.ResetPasswordRequest;
import com.printplatform.model.EmailVerificationToken;
import com.printplatform.model.PasswordResetToken;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.EmailVerificationTokenRepository;
import com.printplatform.repository.PasswordResetTokenRepository;
import com.printplatform.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NOTE: /api/auth/** is throttled per client IP by AuthRateLimitFilter, a singleton bean shared
 * by every test in this class' Spring context (fixed in-memory window, not reset between test
 * methods). The cap is bumped to 100 in src/test/resources/application.properties specifically
 * to give this class headroom — do not hit /api/auth/** from any other test class sharing this
 * same context configuration without accounting for the shared budget.
 */
@Transactional
class AuthControllerTest extends AbstractControllerTest {

    @MockBean
    private EmailService emailService;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Test
    void register_newEmail_returns201AndSendsVerificationEmail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("newbie-" + UUID.randomUUID() + "@test.local");
        request.setPassword("Secret123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail(request.getEmail()).orElseThrow();
        assertThat(saved.isEmailVerified()).isFalse();
        verify(emailService).sendVerificationEmail(eq(request.getEmail()), any(UUID.class));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        User existing = persistUser();

        RegisterRequest request = new RegisterRequest();
        request.setEmail(existing.getEmail());
        request.setPassword("Secret123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidBody_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("not-an-email");
        request.setPassword("123"); // too short

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_passwordMissingUppercase_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("weakpw-" + UUID.randomUUID() + "@test.local");
        request.setPassword("alllowercase1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_verifiedUser_validCredentials_returns200WithToken() throws Exception {
        String rawPassword = "correct-password";
        User user = new User();
        user.setEmail("login-" + UUID.randomUUID() + "@test.local");
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.USER);
        user.setEmailVerified(true);
        userRepository.save(user);

        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword(rawPassword);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        User user = new User();
        user.setEmail("wrongpass-" + UUID.randomUUID() + "@test.local");
        user.setPassword(passwordEncoder.encode("correct-password"));
        user.setRole(Role.USER);
        userRepository.save(user);

        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("totally-wrong");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unverifiedEmail_returns403() throws Exception {
        User user = persistUser(); // persistUser() does not set emailVerified — defaults to false

        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("Password123"); // matches AbstractControllerTest.persistUser()

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void forgotPassword_existingEmail_returns200AndTriggersEmail() throws Exception {
        User user = persistUser();
        doNothing().when(emailService).sendPasswordResetEmail(any(), any());

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(user.getEmail());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(emailService).sendPasswordResetEmail(eq(user.getEmail()), any(UUID.class));
    }

    @Test
    void resetPassword_invalidToken_returns400() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("not-a-uuid");
        request.setNewPassword("brandNewPass1");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_validToken_returns200AndUpdatesPassword() throws Exception {
        User user = persistUser();

        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(user);
        prt.setToken(UUID.randomUUID());
        prt.setExpiresAt(LocalDateTime.now().plusHours(1));
        passwordResetTokenRepository.save(prt);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(prt.getToken().toString());
        request.setNewPassword("brandNewPass1");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("brandNewPass1", reloaded.getPassword())).isTrue();
    }

    @Test
    void verifyEmail_validToken_returns200AndMarksUserVerified() throws Exception {
        User user = persistUser(); // starts unverified

        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setUser(user);
        evt.setToken(UUID.randomUUID());
        evt.setExpiresAt(LocalDateTime.now().plusHours(24));
        emailVerificationTokenRepository.save(evt);

        mockMvc.perform(get("/api/auth/verify-email").param("token", evt.getToken().toString()))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.isEmailVerified()).isTrue();
    }

    @Test
    void verifyEmail_invalidToken_returns400() throws Exception {
        mockMvc.perform(get("/api/auth/verify-email").param("token", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendVerification_unverifiedEmail_returns200AndTriggersEmail() throws Exception {
        User user = persistUser(); // starts unverified

        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail(user.getEmail());

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(emailService).sendVerificationEmail(eq(user.getEmail()), any(UUID.class));
    }
}
