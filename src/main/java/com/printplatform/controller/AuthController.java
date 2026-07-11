package com.printplatform.controller;

import com.printplatform.dto.AuthResponse;
import com.printplatform.dto.FacebookLoginRequest;
import com.printplatform.dto.ForgotPasswordRequest;
import com.printplatform.dto.GoogleLoginRequest;
import com.printplatform.dto.LoginRequest;
import com.printplatform.dto.RegisterRequest;
import com.printplatform.dto.ResendVerificationRequest;
import com.printplatform.dto.ResetPasswordRequest;
import com.printplatform.service.AuthService;
import com.printplatform.service.EmailVerificationService;
import com.printplatform.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthService authService,
                          PasswordResetService passwordResetService,
                          EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/facebook")
    public AuthResponse loginWithFacebook(@Valid @RequestBody FacebookLoginRequest request) {
        return authService.loginWithFacebook(request);
    }

    @PostMapping("/google")
    public AuthResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.OK)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.initReset(request.getEmail());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.OK)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.confirmReset(request.getToken(), request.getNewPassword());
    }

    @GetMapping("/verify-email")
    @ResponseStatus(HttpStatus.OK)
    public void verifyEmail(@RequestParam String token) {
        emailVerificationService.verify(token);
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.OK)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request.getEmail());
    }
}
