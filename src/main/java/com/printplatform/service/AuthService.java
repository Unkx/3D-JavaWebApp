package com.printplatform.service;

import com.printplatform.dto.AuthResponse;
import com.printplatform.dto.FacebookLoginRequest;
import com.printplatform.dto.LoginRequest;
import com.printplatform.dto.RegisterRequest;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.UserRepository;
import com.printplatform.security.FacebookAuthClient;
import com.printplatform.security.JwtService;
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

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AdminService adminService,
                       FacebookAuthClient facebookAuthClient) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.adminService = adminService;
        this.facebookAuthClient = facebookAuthClient;
    }

    public AuthResponse register(RegisterRequest request) {
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

        userRepository.save(user);
        return toResponse(user);
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

        return toResponse(user);
    }

    private User linkFacebookId(User user, String facebookId) {
        user.setFacebookId(facebookId);
        return userRepository.save(user);
    }

    private User createFacebookUser(FacebookAuthClient.FacebookProfile profile) {
        User user = new User();
        user.setEmail(profile.email());
        user.setFacebookId(profile.facebookId());
        user.setFirstName(profile.firstName());
        user.setLastName(profile.lastName());
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    private AuthResponse toResponse(User user) {
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getEmail(), user.getRole().name(), user.getId().toString());
    }
}
