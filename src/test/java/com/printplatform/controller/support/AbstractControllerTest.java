package com.printplatform.controller.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.UserRepository;
import com.printplatform.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

/**
 * Common base for MockMvc controller integration tests: real service beans + H2, no mocking
 * of application logic. Subclasses should add {@code @Transactional} so each test method rolls
 * back its own DB changes.
 *
 * Authentication is done by minting a real JWT via {@link JwtService} for a persisted
 * {@link User} and attaching it as a Bearer token — this mirrors exactly how
 * {@code JwtAuthFilter} authenticates real requests (it reads the Authorization header and looks
 * the user up by the email embedded in the token), and ensures the
 * {@code @AuthenticationPrincipal User} resolved in controllers is our real domain entity rather
 * than Spring Security's generic {@code org.springframework.security.core.userdetails.User}
 * (which is what {@code @WithMockUser} would inject, and is not assignable to our controllers'
 * parameter type).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class AbstractControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    protected User persistUser(Role role) {
        User user = new User();
        user.setEmail("user-" + UUID.randomUUID() + "@test.local");
        user.setPassword(passwordEncoder.encode("Password123"));
        user.setRole(role);
        return userRepository.save(user);
    }

    protected User persistUser() {
        return persistUser(Role.USER);
    }

    protected String bearerToken(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
