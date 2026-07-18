package com.printplatform.service;

import com.printplatform.dto.AdminActionDto;
import com.printplatform.dto.AdminCodeDto;
import com.printplatform.dto.AdminListingDto;
import com.printplatform.dto.AuthResponse;
import com.printplatform.dto.PageResponse;
import com.printplatform.dto.UserSummaryDto;
import com.printplatform.dto.DailyRevenueDto;
import com.printplatform.dto.RevenueSummaryDto;
import com.printplatform.model.AdminAction;
import com.printplatform.model.AdminActionType;
import com.printplatform.model.AdminCode;
import com.printplatform.model.Listing;
import com.printplatform.model.ListingModerationStatus;
import com.printplatform.model.Payment;
import com.printplatform.model.PaymentStatus;
import com.printplatform.model.Rating;
import com.printplatform.model.RatingModerationStatus;
import com.printplatform.dto.RatingDto;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.AdminActionRepository;
import com.printplatform.repository.AdminCodeRepository;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.PaymentRepository;
import com.printplatform.repository.RatingRepository;
import com.printplatform.repository.UserRepository;
import com.printplatform.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final AdminAuditService adminAuditService;
    private final AdminActionRepository adminActionRepository;
    private final PaymentRepository paymentRepository;
    private final RatingRepository ratingRepository;
    private final UserDisplayNameService userDisplayNameService;

    public AdminService(AdminCodeRepository codeRepository,
                        UserRepository userRepository,
                        ListingRepository listingRepository,
                        JwtService jwtService,
                        AdminAuditService adminAuditService,
                        AdminActionRepository adminActionRepository,
                        PaymentRepository paymentRepository,
                        RatingRepository ratingRepository,
                        UserDisplayNameService userDisplayNameService) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.jwtService = jwtService;
        this.adminAuditService = adminAuditService;
        this.adminActionRepository = adminActionRepository;
        this.paymentRepository = paymentRepository;
        this.ratingRepository = ratingRepository;
        this.userDisplayNameService = userDisplayNameService;
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

    /** Suspends a user's account: blocks future logins and revokes any already-issued JWT (admin only). */
    public UserSummaryDto suspendUser(User admin, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Użytkownik nie istnieje"));
        user.setSuspended(true);
        userRepository.save(user);
        adminAuditService.log(admin, AdminActionType.BAN_USER, "User", user.getId(), null);
        return new UserSummaryDto(user);
    }

    /** Lifts a suspension (admin only). */
    public UserSummaryDto unsuspendUser(User admin, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Użytkownik nie istnieje"));
        user.setSuspended(false);
        userRepository.save(user);
        adminAuditService.log(admin, AdminActionType.UNBAN_USER, "User", user.getId(), null);
        return new UserSummaryDto(user);
    }

    /** Hides a listing from the public feed without deleting it (admin only). */
    public AdminListingDto hideListing(User admin, UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
        listing.setModerationStatus(ListingModerationStatus.HIDDEN);
        listingRepository.save(listing);
        adminAuditService.log(admin, AdminActionType.HIDE_LISTING, "Listing", listing.getId(), null);
        return new AdminListingDto(listing);
    }

    /** Restores a previously hidden listing to the public feed (admin only). */
    public AdminListingDto unhideListing(User admin, UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
        listing.setModerationStatus(ListingModerationStatus.VISIBLE);
        listingRepository.save(listing);
        adminAuditService.log(admin, AdminActionType.UNHIDE_LISTING, "Listing", listing.getId(), null);
        return new AdminListingDto(listing);
    }

    /** Hides a rating from public view without deleting it (admin only). */
    public RatingDto hideRating(User admin, UUID ratingId) {
        Rating rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ocena nie istnieje"));
        rating.setModerationStatus(RatingModerationStatus.HIDDEN);
        ratingRepository.save(rating);
        adminAuditService.log(admin, AdminActionType.HIDE_RATING, "Rating", rating.getId(), null);
        return new RatingDto(rating, resolveRaterDisplayName(rating.getRaterId()));
    }

    /** Restores a previously hidden rating to public view (admin only). */
    public RatingDto unhideRating(User admin, UUID ratingId) {
        Rating rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ocena nie istnieje"));
        rating.setModerationStatus(RatingModerationStatus.VISIBLE);
        ratingRepository.save(rating);
        adminAuditService.log(admin, AdminActionType.UNHIDE_RATING, "Rating", rating.getId(), null);
        return new RatingDto(rating, resolveRaterDisplayName(rating.getRaterId()));
    }

    /** All ratings (visible and hidden), newest first, for the moderation list (admin only). */
    public PageResponse<RatingDto> getAllRatings(int page, int size) {
        int safeSize = Math.clamp(size, 1, 100);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Rating> result = ratingRepository.findAllByOrderByCreatedAtDesc(pageable);
        return new PageResponse<>(result.map(r -> new RatingDto(r, resolveRaterDisplayName(r.getRaterId()))));
    }

    /** Paged, newest-first admin action history (admin only). */
    public PageResponse<AdminActionDto> getAuditLog(int page, int size) {
        int safeSize = Math.clamp(size, 1, 100);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<AdminAction> result = adminActionRepository.findAllByOrderByCreatedAtDesc(pageable);
        return new PageResponse<>(result.map(AdminActionDto::new));
    }

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** Revenue summary aggregated in Java (not SQL date-grouping) over "realized" payments — HELD or RELEASED,
     *  i.e. money actually captured; PENDING and REFUNDED are excluded from the sums but PENDING is still counted. */
    public RevenueSummaryDto getRevenueSummary(int days) {
        int safeDays = Math.clamp(days, 1, 90);
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays);
        List<Payment> payments = paymentRepository.findByCreatedAtAfter(since);

        Map<String, List<Payment>> byDayRaw = payments.stream()
                .collect(Collectors.groupingBy(p -> p.getCreatedAt().format(DAY_FORMAT), TreeMap::new, Collectors.toList()));

        List<DailyRevenueDto> byDay = byDayRaw.entrySet().stream()
                .map(e -> new DailyRevenueDto(
                        e.getKey(),
                        sumRealized(e.getValue(), Payment::getPlatformFee),
                        sumRealized(e.getValue(), Payment::getTotalPrice)))
                .toList();

        BigDecimal totalFee = sumRealized(payments, Payment::getPlatformFee);
        BigDecimal totalVolume = sumRealized(payments, Payment::getTotalPrice);
        long paidCount = payments.stream().filter(this::isRealized).count();
        long pendingCount = payments.stream().filter(p -> p.getStatus() == PaymentStatus.PENDING).count();

        return new RevenueSummaryDto(byDay, totalFee, totalVolume, paidCount, pendingCount);
    }

    private boolean isRealized(Payment p) {
        return p.getStatus() == PaymentStatus.HELD || p.getStatus() == PaymentStatus.RELEASED;
    }

    private BigDecimal sumRealized(List<Payment> payments, Function<Payment, BigDecimal> field) {
        return payments.stream()
                .filter(this::isRealized)
                .map(field)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String resolveRaterDisplayName(UUID raterId) {
        return userRepository.findById(raterId)
                .map(userDisplayNameService::resolve)
                .orElse("Użytkownik");
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
