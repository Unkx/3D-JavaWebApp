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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AdminService adminService;
    @Mock
    private FacebookAuthClient facebookAuthClient;
    @Mock
    private GoogleAuthClient googleAuthClient;
    @Mock
    private EmailVerificationService emailVerificationService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, adminService,
                facebookAuthClient, googleAuthClient, emailVerificationService);
    }

    private User buildUser(String email, String encodedPassword, Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPassword(encodedPassword);
        user.setRole(role);
        user.setEmailVerified(true);
        return user;
    }

    @Test
    void register_newEmail_savesUnverifiedUserAndSendsVerificationEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("Secret123");

        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Secret123")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        authService.register(request);

        verify(userRepository).save(argThat((User u) ->
                u.getEmail().equals("new@example.com")
                        && u.getPassword().equals("encoded-secret")
                        && u.getRole() == Role.USER
                        && !u.isEmailVerified()));
        verify(emailVerificationService).issueAndSendToken(argThat(u -> u.getEmail().equals("new@example.com")));
        verifyNoInteractions(adminService);
        verifyNoInteractions(jwtService);
    }

    @Test
    void register_existingEmail_throwsConflict() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("taken@example.com");
        request.setPassword("secret123");

        when(userRepository.findByEmail("taken@example.com"))
                .thenReturn(Optional.of(buildUser("taken@example.com", "x", Role.USER)));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void register_withNonBlankAdminCode_appliesCodeToNewUser() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("admin-to-be@example.com");
        request.setPassword("Secret123");
        request.setAdminCode("ABCD-1234-EFGH");

        when(userRepository.findByEmail("admin-to-be@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Secret123")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        authService.register(request);

        verify(adminService).applyCodeToNewUser(eq("ABCD-1234-EFGH"), any(User.class));
    }

    @Test
    void register_withBlankAdminCode_doesNotApplyCode() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("plain@example.com");
        request.setPassword("Secret123");
        request.setAdminCode("   ");

        when(userRepository.findByEmail("plain@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Secret123")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        authService.register(request);

        verifyNoInteractions(adminService);
    }

    @Test
    void register_withNullAdminCode_doesNotApplyCode() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("plain2@example.com");
        request.setPassword("Secret123");
        request.setAdminCode(null);

        when(userRepository.findByEmail("plain2@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Secret123")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        authService.register(request);

        verifyNoInteractions(adminService);
    }

    @Test
    void login_validCredentials_returnsAuthResponse() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret123");

        User user = buildUser("user@example.com", "encoded-secret", Role.USER);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "encoded-secret")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getUserId()).isEqualTo(user.getId().toString());
    }

    @Test
    void login_unknownEmail_throwsUnauthorized() {
        LoginRequest request = new LoginRequest();
        request.setEmail("ghost@example.com");
        request.setPassword("secret123");

        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("wrong-password");

        User user = buildUser("user@example.com", "encoded-secret", Role.USER);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-secret")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_unverifiedEmail_throwsForbidden() {
        LoginRequest request = new LoginRequest();
        request.setEmail("unverified@example.com");
        request.setPassword("secret123");

        User user = buildUser("unverified@example.com", "encoded-secret", Role.USER);
        user.setEmailVerified(false);
        when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "encoded-secret")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_suspendedUser_throwsForbidden() {
        User user = buildUser("suspended@example.com", "encoded", Role.USER);
        user.setSuspended(true);
        LoginRequest request = new LoginRequest();
        request.setEmail("suspended@example.com");
        request.setPassword("Secret123");

        when(userRepository.findByEmail("suspended@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123", "encoded")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    void login_facebookOnlyAccount_throwsUnauthorizedWithoutCheckingPassword() {
        LoginRequest request = new LoginRequest();
        request.setEmail("fbonly@example.com");
        request.setPassword("whatever");

        User user = buildUser("fbonly@example.com", null, Role.USER);
        when(userRepository.findByEmail("fbonly@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void loginWithFacebook_newUser_createsAccountAndReturnsAuthResponse() {
        FacebookLoginRequest request = new FacebookLoginRequest();
        request.setAccessToken("fb-access-token");

        FacebookAuthClient.FacebookProfile profile =
                new FacebookAuthClient.FacebookProfile("fb123", "newfb@example.com", "Jan", "Kowalski");
        when(facebookAuthClient.verify("fb-access-token")).thenReturn(profile);
        when(userRepository.findByFacebookId("fb123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("newfb@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");

        AuthResponse response = authService.loginWithFacebook(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("newfb@example.com");
        verify(userRepository).save(argThat((User u) ->
                u.getEmail().equals("newfb@example.com")
                        && "fb123".equals(u.getFacebookId())
                        && u.getPassword() == null
                        && u.getFirstName().equals("Jan")
                        && u.getRole() == Role.USER
                        && u.isEmailVerified()));
    }

    @Test
    void loginWithFacebook_existingEmailPasswordAccount_autoLinksFacebookId() {
        FacebookLoginRequest request = new FacebookLoginRequest();
        request.setAccessToken("fb-access-token");

        FacebookAuthClient.FacebookProfile profile =
                new FacebookAuthClient.FacebookProfile("fb456", "existing@example.com", "Anna", "Nowak");
        User existing = buildUser("existing@example.com", "encoded-secret", Role.USER);
        when(facebookAuthClient.verify("fb-access-token")).thenReturn(profile);
        when(userRepository.findByFacebookId("fb456")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(jwtService.generateToken(existing)).thenReturn("jwt-token");

        AuthResponse response = authService.loginWithFacebook(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(existing.getFacebookId()).isEqualTo("fb456");
        verify(userRepository).save(existing);
    }

    @Test
    void loginWithFacebook_existingFacebookUser_logsInWithoutSaving() {
        FacebookLoginRequest request = new FacebookLoginRequest();
        request.setAccessToken("fb-access-token");

        FacebookAuthClient.FacebookProfile profile =
                new FacebookAuthClient.FacebookProfile("fb789", "repeat@example.com", "Piotr", "Zielinski");
        User existing = buildUser("repeat@example.com", null, Role.USER);
        existing.setFacebookId("fb789");
        when(facebookAuthClient.verify("fb-access-token")).thenReturn(profile);
        when(userRepository.findByFacebookId("fb789")).thenReturn(Optional.of(existing));
        when(jwtService.generateToken(existing)).thenReturn("jwt-token");

        AuthResponse response = authService.loginWithFacebook(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithFacebook_missingEmail_throwsBadRequest() {
        FacebookLoginRequest request = new FacebookLoginRequest();
        request.setAccessToken("fb-access-token");

        FacebookAuthClient.FacebookProfile profile =
                new FacebookAuthClient.FacebookProfile("fb999", null, "No", "Email");
        when(facebookAuthClient.verify("fb-access-token")).thenReturn(profile);

        assertThatThrownBy(() -> authService.loginWithFacebook(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithGoogle_newUser_createsAccountAndReturnsAuthResponse() {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("google-id-token");

        GoogleAuthClient.GoogleProfile profile =
                new GoogleAuthClient.GoogleProfile("google123", "newgoogle@example.com", "Anna", "Nowak");
        when(googleAuthClient.verify("google-id-token")).thenReturn(profile);
        when(userRepository.findByGoogleId("google123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("newgoogle@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");

        AuthResponse response = authService.loginWithGoogle(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("newgoogle@example.com");
        verify(userRepository).save(argThat((User u) ->
                u.getEmail().equals("newgoogle@example.com")
                        && "google123".equals(u.getGoogleId())
                        && u.getPassword() == null
                        && u.getFirstName().equals("Anna")
                        && u.getRole() == Role.USER
                        && u.isEmailVerified()));
    }

    @Test
    void loginWithGoogle_existingGoogleId_logsInWithoutSaving() {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("google-id-token");

        GoogleAuthClient.GoogleProfile profile =
                new GoogleAuthClient.GoogleProfile("google456", "repeat@example.com", "Piotr", "Zielinski");
        User existing = buildUser("repeat@example.com", null, Role.USER);
        existing.setGoogleId("google456");
        when(googleAuthClient.verify("google-id-token")).thenReturn(profile);
        when(userRepository.findByGoogleId("google456")).thenReturn(Optional.of(existing));
        when(jwtService.generateToken(existing)).thenReturn("jwt-token");

        AuthResponse response = authService.loginWithGoogle(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithGoogle_emailAlreadyExistsAsPasswordAccount_throwsConflict() {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("google-id-token");

        GoogleAuthClient.GoogleProfile profile =
                new GoogleAuthClient.GoogleProfile("google789", "existing@example.com", "Ola", "Kowal");
        User existingPasswordUser = buildUser("existing@example.com", "encoded-secret", Role.USER);
        when(googleAuthClient.verify("google-id-token")).thenReturn(profile);
        when(userRepository.findByGoogleId("google789")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingPasswordUser));

        assertThatThrownBy(() -> authService.loginWithGoogle(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithGoogle_emailAlreadyExistsAsFacebookAccount_throwsConflict() {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("google-id-token");

        GoogleAuthClient.GoogleProfile profile =
                new GoogleAuthClient.GoogleProfile("google999", "fbuser@example.com", "Tom", "Nowicki");
        User existingFacebookUser = buildUser("fbuser@example.com", null, Role.USER);
        existingFacebookUser.setFacebookId("fb-existing-1");
        when(googleAuthClient.verify("google-id-token")).thenReturn(profile);
        when(userRepository.findByGoogleId("google999")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("fbuser@example.com")).thenReturn(Optional.of(existingFacebookUser));

        assertThatThrownBy(() -> authService.loginWithGoogle(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(userRepository, never()).save(any());
    }
}
