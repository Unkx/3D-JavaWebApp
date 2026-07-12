package com.printplatform.service;

import com.printplatform.dto.AdminCodeDto;
import com.printplatform.dto.AdminListingDto;
import com.printplatform.dto.AuthResponse;
import com.printplatform.dto.UserSummaryDto;
import com.printplatform.model.AdminCode;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.AdminCodeRepository;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.UserRepository;
import com.printplatform.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    // No ambiguous chars (0/O, 1/I) to make codes easy to read and type
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final AdminCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final JwtService jwtService;

    public AdminService(AdminCodeRepository codeRepository,
                        UserRepository userRepository,
                        ListingRepository listingRepository,
                        JwtService jwtService) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.jwtService = jwtService;
    }

    /** Admin generates a new single-use admin code. */
    public AdminCodeDto generateCode(User admin) {
        AdminCode code = new AdminCode();
        code.setCode(randomCode());
        code.setCreatedByEmail(admin.getEmail());
        codeRepository.save(code);
        return new AdminCodeDto(code);
    }

    /** List all listings, newest first (admin only). */
    public List<AdminListingDto> listAllListings() {
        return listingRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(AdminListingDto::new)
                .toList();
    }

    /** List all users, newest first (admin only). */
    public List<UserSummaryDto> listUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(UserSummaryDto::new)
                .toList();
    }

    /** List all admin codes, newest first (admin only). */
    public List<AdminCodeDto> listCodes() {
        return codeRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(AdminCodeDto::new)
                .toList();
    }

    /** A logged-in user redeems a code to become admin. Returns a fresh token with the new role. */
    @Transactional
    public AuthResponse redeemCode(User user, String rawCode) {
        if (user.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jesteś już administratorem");
        }
        AdminCode code;
        try {
            code = validate(rawCode);
        } catch (ResponseStatusException e) {
            // Not rate-limited by attempt count, only by IP+window (AuthRateLimitFilter),
            // so a log line here is the only record of a guessing attempt against this endpoint.
            log.warn("Failed admin-code redeem attempt by {}: {}", user.getEmail(), e.getReason());
            throw e;
        }
        claimCode(code, user.getEmail());
        user.setRole(Role.ADMIN);
        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getEmail(), user.getRole().name(), user.getId().toString());
    }

    /** Used during registration: validate a code and promote the (not-yet-saved) user to admin. */
    @Transactional
    public void applyCodeToNewUser(String rawCode, User user) {
        AdminCode code = validate(rawCode);
        claimCode(code, user.getEmail());
        user.setRole(Role.ADMIN);
    }

    private AdminCode validate(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kod administratora jest pusty");
        }
        AdminCode code = codeRepository.findByCode(rawCode.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowy kod administratora"));
        if (code.isUsed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ten kod został już wykorzystany");
        }
        return code;
    }

    /**
     * Atomically claims the code (conditional UPDATE ... WHERE used = false). If a concurrent
     * request already claimed it between validate()'s read and this write, the update affects
     * zero rows and we reject this request instead of silently double-promoting.
     */
    private void claimCode(AdminCode code, String userEmail) {
        int updated = codeRepository.markUsedIfUnused(code.getId(), userEmail, LocalDateTime.now());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ten kod został już wykorzystany");
        }
    }

    private String randomCode() {
        // Format: XXXX-XXXX-XXXX
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
