package com.printplatform.service;

import com.printplatform.dto.AuthResponse;
import com.printplatform.dto.FacebookLoginRequest;
import com.printplatform.dto.GoogleLoginRequest;
import com.printplatform.dto.LoginRequest;
import com.printplatform.dto.RegisterRequest;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.UserRepository;
import com.printplatform.security.FacebookAuthClient;
import com.printplatform.security.GoogleAuthClient;
import com.printplatform.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminService adminService;
    private final FacebookAuthClient facebookAuthClient;
    private final GoogleAuthClient googleAuthClient;
    private final EmailVerificationService emailVerificationService;

    /** Escape hatch for local dev and e2e tests only. Never enable in a real environment. */
    @Value("${app.security.auto-verify-email:false}")
    private boolean autoVerifyEmail;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AdminService adminService,
                       FacebookAuthClient facebookAuthClient,
                       GoogleAuthClient googleAuthClient,
                       EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.adminService = adminService;
        this.facebookAuthClient = facebookAuthClient;
        this.googleAuthClient = googleAuthClient;
        this.emailVerificationService = emailVerificationService;
    }

    public void register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Konto z tym emailem już istnieje");
        }
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);

        // Optional admin code promotes the new account to administrator.
        if (request.getAdminCode() != null && !request.getAdminCode().isBlank()) {
            adminService.applyCodeToNewUser(request.getAdminCode(), user);
        }

        if (autoVerifyEmail) {
            user.setEmailVerified(true);
        }

        userRepository.save(user);

        if (!autoVerifyEmail) {
            emailVerificationService.issueAndSendToken(user);
        }
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowy email lub hasło"));

        if (user.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "To konto używa logowania przez Facebook.");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowy email lub hasło");
        }
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Potwierdź adres email, aby się zalogować. Sprawdź swoją skrzynkę pocztową.");
        }
        touchLogin(user);
        return toResponse(user);
    }

    public AuthResponse loginWithFacebook(FacebookLoginRequest request) {
        FacebookAuthClient.FacebookProfile profile = facebookAuthClient.verify(request.getAccessToken());

        if (profile.email() == null || profile.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Potrzebujemy dostępu do Twojego emaila, aby się zalogować.");
        }

        // Auto-link trusts profile.email() as verified by Facebook (Graph API only returns
        // a confirmed email address) — do not extend this pattern to a provider that doesn't
        // guarantee email verification.
        User user = userRepository.findByFacebookId(profile.facebookId())
                .orElseGet(() -> userRepository.findByEmail(profile.email())
                        .map(existing -> linkFacebookId(existing, profile.facebookId()))
                        .orElseGet(() -> createFacebookUser(profile)));

        touchLogin(user);
        return toResponse(user);
    }

    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleAuthClient.GoogleProfile profile = googleAuthClient.verify(request.getIdToken());

        // Unlike Facebook, Google logins do NOT auto-link onto an existing account —
        // reject instead, so the user goes back to whichever method that account already uses.
        User user = userRepository.findByGoogleId(profile.googleId())
                .orElseGet(() -> {
                    if (userRepository.findByEmail(profile.email()).isPresent()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Konto z tym adresem email już istnieje. Zaloguj się przez email lub Facebook.");
                    }
                    return createGoogleUser(profile);
                });

        applyGoogleLogin(user, profile.picture());
        return toResponse(user);
    }

    private User linkFacebookId(User user, String facebookId) {
        user.setFacebookId(facebookId);
        return userRepository.save(user);
    }

    /** Records that a login happened; used for password and Facebook logins, which have no avatar signal. */
    private void touchLogin(User user) {
        user.setLastLoginAt(java.time.LocalDateTime.now());
        userRepository.save(user);
    }

    /**
     * Records the login and caches Google's current profile picture URL. If the user has never
     * had any avatar (no upload, no prior import) and hasn't explicitly opted out via a delete,
     * auto-imports it as the active avatar — but only once; a later manual upload or deletion
     * sets avatarImportSkipped so this never silently overwrites the user's choice again.
     */
    private void applyGoogleLogin(User user, String picture) {
        user.setGoogleAvatarUrl(picture);
        boolean neverHadAnAvatar = user.getAvatarData() == null && user.getAvatarUrl() == null;
        if (!user.isAvatarImportSkipped() && neverHadAnAvatar && picture != null) {
            user.setAvatarUrl(picture);
            user.setAvatarImportSkipped(true);
        }
        user.setLastLoginAt(java.time.LocalDateTime.now());
        userRepository.save(user);
    }

    private User createFacebookUser(FacebookAuthClient.FacebookProfile profile) {
        User user = new User();
        user.setEmail(profile.email());
        user.setFacebookId(profile.facebookId());
        user.setFirstName(profile.firstName());
        user.setLastName(profile.lastName());
        user.setRole(Role.USER);
        // Graph API only returns a confirmed email address — no separate verification needed.
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private User createGoogleUser(GoogleAuthClient.GoogleProfile profile) {
        User user = new User();
        user.setEmail(profile.email());
        user.setGoogleId(profile.googleId());
        user.setFirstName(profile.firstName());
        user.setLastName(profile.lastName());
        user.setRole(Role.USER);
        // GoogleAuthClient.verify() already rejects tokens where email_verified != true.
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private AuthResponse toResponse(User user) {
        if (user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "To konto zostało zawieszone.");
        }
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getEmail(), user.getRole().name(), user.getId().toString());
    }
}
