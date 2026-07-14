package com.printplatform.service;

import com.printplatform.dto.AdminActionDto;
import com.printplatform.dto.AdminCodeDto;
import com.printplatform.dto.AdminListingDto;
import com.printplatform.dto.AuthResponse;
import com.printplatform.dto.PageResponse;
import com.printplatform.dto.RatingDto;
import com.printplatform.dto.UserSummaryDto;
import com.printplatform.model.AdminAction;
import com.printplatform.model.AdminActionType;
import com.printplatform.model.AdminCode;
import com.printplatform.model.Listing;
import com.printplatform.model.ListingModerationStatus;
import com.printplatform.model.ListingStatus;
import com.printplatform.model.Payment;
import com.printplatform.model.PaymentStatus;
import com.printplatform.model.Rating;
import com.printplatform.model.RatingModerationStatus;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.dto.RevenueSummaryDto;
import com.printplatform.repository.AdminActionRepository;
import com.printplatform.repository.AdminCodeRepository;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.PaymentRepository;
import com.printplatform.repository.RatingRepository;
import com.printplatform.repository.UserRepository;
import com.printplatform.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AdminCodeRepository codeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private AdminAuditService adminAuditService;
    @Mock
    private AdminActionRepository adminActionRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RatingRepository ratingRepository;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(codeRepository, userRepository, listingRepository, jwtService,
                adminAuditService, adminActionRepository, paymentRepository, ratingRepository);
    }

    private User buildUser(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setRole(role);
        return user;
    }

    @Test
    void generateCode_savesAndReturnsDtoWithCreatorEmail() {
        User admin = buildUser(Role.ADMIN);
        admin.setEmail("admin@example.com");

        when(codeRepository.save(any(AdminCode.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminCodeDto dto = adminService.generateCode(admin);

        assertThat(dto.getCreatedByEmail()).isEqualTo("admin@example.com");
        assertThat(dto.getCode()).isNotBlank();
        assertThat(dto.getCode()).matches("^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$");
        assertThat(dto.isUsed()).isFalse();
        verify(codeRepository).save(any(AdminCode.class));
    }

    @Test
    void listAllListings_mapsRepositoryResultsToDtos() {
        Listing listing = new Listing();
        listing.setId(UUID.randomUUID());
        listing.setTitle("Test listing");
        listing.setStatus(ListingStatus.OPEN);
        User owner = buildUser(Role.USER);
        listing.setUser(owner);

        when(listingRepository.findAll(any(Sort.class))).thenReturn(List.of(listing));

        List<AdminListingDto> result = adminService.listAllListings();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Test listing");
        assertThat(result.get(0).getOwnerEmail()).isEqualTo("user@example.com");
    }

    @Test
    void listUsers_mapsRepositoryResultsToDtos() {
        User user = buildUser(Role.USER);
        when(userRepository.findAll(any(Sort.class))).thenReturn(List.of(user));

        List<UserSummaryDto> result = adminService.listUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void listCodes_mapsRepositoryResultsToDtos() {
        AdminCode code = new AdminCode();
        code.setCode("ABCD-EFGH-JKLM");
        when(codeRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(code));

        List<AdminCodeDto> result = adminService.listCodes();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("ABCD-EFGH-JKLM");
    }

    @Test
    void redeemCode_validCodeForRegularUser_promotesUserAndReturnsFreshToken() {
        User user = buildUser(Role.USER);
        AdminCode code = new AdminCode();
        code.setCode("ABCD-EFGH-JKLM");
        code.setUsed(false);

        when(codeRepository.findByCode("ABCD-EFGH-JKLM")).thenReturn(Optional.of(code));
        when(codeRepository.markUsedIfUnused(eq(code.getId()), eq(user.getEmail()), any())).thenReturn(1);
        when(jwtService.generateToken(user)).thenReturn("new-jwt-token");

        AuthResponse response = adminService.redeemCode(user, "abcd-efgh-jklm");

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(response.getToken()).isEqualTo("new-jwt-token");
        assertThat(response.getRole()).isEqualTo(Role.ADMIN.name());
        verify(userRepository).save(user);
        verify(codeRepository).markUsedIfUnused(eq(code.getId()), eq(user.getEmail()), any());
    }

    @Test
    void redeemCode_concurrentlyClaimedCode_throwsBadRequestAndDoesNotPromote() {
        User user = buildUser(Role.USER);
        AdminCode code = new AdminCode();
        code.setCode("ABCD-EFGH-JKLM");
        code.setUsed(false);

        when(codeRepository.findByCode("ABCD-EFGH-JKLM")).thenReturn(Optional.of(code));
        // Simulates another request winning the race between validate() and the claim.
        when(codeRepository.markUsedIfUnused(eq(code.getId()), eq(user.getEmail()), any())).thenReturn(0);

        assertThatThrownBy(() -> adminService.redeemCode(user, "ABCD-EFGH-JKLM"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(user.getRole()).isEqualTo(Role.USER);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(jwtService);
    }

    @Test
    void redeemCode_userAlreadyAdmin_throwsBadRequest() {
        User admin = buildUser(Role.ADMIN);

        assertThatThrownBy(() -> adminService.redeemCode(admin, "ABCD-EFGH-JKLM"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(codeRepository);
    }

    @Test
    void redeemCode_blankCode_throwsBadRequest() {
        User user = buildUser(Role.USER);

        assertThatThrownBy(() -> adminService.redeemCode(user, "   "))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(codeRepository, never()).findByCode(anyString());
    }

    @Test
    void redeemCode_unknownCode_throwsBadRequest() {
        User user = buildUser(Role.USER);
        when(codeRepository.findByCode("UNKNOWN-CODE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.redeemCode(user, "unknown-code"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void redeemCode_alreadyUsedCode_throwsBadRequest() {
        User user = buildUser(Role.USER);
        AdminCode code = new AdminCode();
        code.setCode("ABCD-EFGH-JKLM");
        code.setUsed(true);

        when(codeRepository.findByCode("ABCD-EFGH-JKLM")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> adminService.redeemCode(user, "ABCD-EFGH-JKLM"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void applyCodeToNewUser_validCode_promotesUserWithoutSavingUser() {
        User user = buildUser(Role.USER);
        AdminCode code = new AdminCode();
        code.setCode("ABCD-EFGH-JKLM");
        code.setUsed(false);

        when(codeRepository.findByCode("ABCD-EFGH-JKLM")).thenReturn(Optional.of(code));
        when(codeRepository.markUsedIfUnused(eq(code.getId()), eq(user.getEmail()), any())).thenReturn(1);

        adminService.applyCodeToNewUser("abcd-efgh-jklm", user);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        verify(codeRepository).markUsedIfUnused(eq(code.getId()), eq(user.getEmail()), any());
        verifyNoInteractions(userRepository);
        verifyNoInteractions(jwtService);
    }

    @Test
    void applyCodeToNewUser_invalidCode_throwsBadRequestAndDoesNotPromote() {
        User user = buildUser(Role.USER);
        when(codeRepository.findByCode("BAD-CODE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.applyCodeToNewUser("BAD-CODE", user))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void suspendUser_marksSuspendedAndLogsAudit() {
        User admin = buildUser(Role.ADMIN);
        admin.setEmail("admin@example.com");
        User target = buildUser(Role.USER);

        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserSummaryDto result = adminService.suspendUser(admin, target.getId());

        assertThat(target.isSuspended()).isTrue();
        assertThat(result.getId()).isEqualTo(target.getId().toString());
        verify(adminAuditService).log(admin, AdminActionType.BAN_USER, "User", target.getId(), null);
    }

    @Test
    void unsuspendUser_clearsSuspendedAndLogsAudit() {
        User admin = buildUser(Role.ADMIN);
        User target = buildUser(Role.USER);
        target.setSuspended(true);

        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        adminService.unsuspendUser(admin, target.getId());

        assertThat(target.isSuspended()).isFalse();
        verify(adminAuditService).log(admin, AdminActionType.UNBAN_USER, "User", target.getId(), null);
    }

    @Test
    void suspendUser_unknownUser_throwsNotFound() {
        User admin = buildUser(Role.ADMIN);
        UUID missingId = UUID.randomUUID();
        when(userRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.suspendUser(admin, missingId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void hideListing_marksHiddenAndLogsAudit() {
        User admin = buildUser(Role.ADMIN);
        Listing listing = new Listing();
        listing.setId(UUID.randomUUID());
        listing.setTitle("Suspicious listing");
        listing.setUser(buildUser(Role.USER));

        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        adminService.hideListing(admin, listing.getId());

        assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.HIDDEN);
        verify(adminAuditService).log(admin, AdminActionType.HIDE_LISTING, "Listing", listing.getId(), null);
    }

    @Test
    void unhideListing_marksVisibleAndLogsAudit() {
        User admin = buildUser(Role.ADMIN);
        Listing listing = new Listing();
        listing.setId(UUID.randomUUID());
        listing.setTitle("Reviewed listing");
        listing.setUser(buildUser(Role.USER));
        listing.setModerationStatus(ListingModerationStatus.HIDDEN);

        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        adminService.unhideListing(admin, listing.getId());

        assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.VISIBLE);
        verify(adminAuditService).log(admin, AdminActionType.UNHIDE_LISTING, "Listing", listing.getId(), null);
    }

    @Test
    void hideRating_marksHiddenAndLogsAudit() {
        User admin = buildUser(Role.ADMIN);
        Rating rating = new Rating();
        rating.setId(UUID.randomUUID());
        rating.setOfferId(UUID.randomUUID());
        rating.setRaterId(UUID.randomUUID());
        rating.setRatedUserId(UUID.randomUUID());
        rating.setStars(1);

        when(ratingRepository.findById(rating.getId())).thenReturn(Optional.of(rating));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));

        adminService.hideRating(admin, rating.getId());

        assertThat(rating.getModerationStatus()).isEqualTo(RatingModerationStatus.HIDDEN);
        verify(adminAuditService).log(admin, AdminActionType.HIDE_RATING, "Rating", rating.getId(), null);
    }

    @Test
    void unhideRating_marksVisibleAndLogsAudit() {
        User admin = buildUser(Role.ADMIN);
        Rating rating = new Rating();
        rating.setId(UUID.randomUUID());
        rating.setOfferId(UUID.randomUUID());
        rating.setRaterId(UUID.randomUUID());
        rating.setRatedUserId(UUID.randomUUID());
        rating.setStars(1);
        rating.setModerationStatus(RatingModerationStatus.HIDDEN);

        when(ratingRepository.findById(rating.getId())).thenReturn(Optional.of(rating));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));

        adminService.unhideRating(admin, rating.getId());

        assertThat(rating.getModerationStatus()).isEqualTo(RatingModerationStatus.VISIBLE);
        verify(adminAuditService).log(admin, AdminActionType.UNHIDE_RATING, "Rating", rating.getId(), null);
    }

    @Test
    void getAuditLog_returnsPagedDtos() {
        AdminAction action = new AdminAction();
        action.setId(UUID.randomUUID());
        action.setAdminId(UUID.randomUUID());
        action.setAdminEmail("admin@example.com");
        action.setActionType(AdminActionType.HIDE_LISTING);
        action.setTargetType("Listing");
        action.setTargetId(UUID.randomUUID());

        Page<AdminAction> page = new PageImpl<>(List.of(action));
        when(adminActionRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(page);

        PageResponse<AdminActionDto> result = adminService.getAuditLog(0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getActionType()).isEqualTo("HIDE_LISTING");
        assertThat(result.getContent().get(0).getAdminEmail()).isEqualTo("admin@example.com");
    }

    private Payment payment(PaymentStatus status, BigDecimal fee, BigDecimal total, LocalDateTime createdAt) {
        Payment p = new Payment();
        p.setContractorPrice(BigDecimal.TEN);
        p.setPlatformFeePercent(BigDecimal.TEN);
        p.setPlatformFee(fee);
        p.setShippingPrice(BigDecimal.ZERO);
        p.setTotalPrice(total);
        p.setStatus(status);
        p.setCreatedAt(createdAt);
        return p;
    }

    @Test
    void getRevenueSummary_sumsOnlyRealizedPayments() {
        LocalDateTime now = LocalDateTime.now();
        when(paymentRepository.findByCreatedAtAfter(any())).thenReturn(List.of(
                payment(PaymentStatus.RELEASED, new BigDecimal("10.00"), new BigDecimal("100.00"), now),
                payment(PaymentStatus.HELD, new BigDecimal("5.00"), new BigDecimal("50.00"), now),
                payment(PaymentStatus.PENDING, new BigDecimal("999.00"), new BigDecimal("999.00"), now)
        ));

        RevenueSummaryDto result = adminService.getRevenueSummary(7);

        assertThat(result.getTotalPlatformFee()).isEqualByComparingTo("15.00");
        assertThat(result.getTotalVolume()).isEqualByComparingTo("150.00");
        assertThat(result.getPaidCount()).isEqualTo(2);
        assertThat(result.getPendingCount()).isEqualTo(1);
        assertThat(result.getByDay()).hasSize(1);
    }

    @Test
    void getRevenueSummary_clampsExcessivelyLargeDaysTo90() {
        when(paymentRepository.findByCreatedAtAfter(any())).thenReturn(List.of());

        adminService.getRevenueSummary(100_000);

        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(paymentRepository).findByCreatedAtAfter(sinceCaptor.capture());

        // Clamped to 90 days back, not ~274 years (100_000 days) back.
        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
        assertThat(ChronoUnit.SECONDS.between(ninetyDaysAgo, sinceCaptor.getValue())).isLessThan(5);
    }

    @Test
    void getRevenueSummary_negativeDaysDoesNotThrowAndClampsToOne() {
        when(paymentRepository.findByCreatedAtAfter(any())).thenReturn(List.of());

        RevenueSummaryDto result = adminService.getRevenueSummary(-5);

        assertThat(result).isNotNull();
        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(paymentRepository).findByCreatedAtAfter(sinceCaptor.capture());
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        assertThat(ChronoUnit.SECONDS.between(oneDayAgo, sinceCaptor.getValue())).isLessThan(5);
    }

    @Test
    void getAllRatings_returnsPagedDtosRegardlessOfModerationStatus() {
        Rating hidden = new Rating();
        hidden.setId(UUID.randomUUID());
        hidden.setOfferId(UUID.randomUUID());
        hidden.setRaterId(UUID.randomUUID());
        hidden.setRatedUserId(UUID.randomUUID());
        hidden.setStars(1);
        hidden.setModerationStatus(RatingModerationStatus.HIDDEN);

        org.springframework.data.domain.Page<Rating> page =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(hidden));
        when(ratingRepository.findAllByOrderByCreatedAtDesc(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        com.printplatform.dto.PageResponse<com.printplatform.dto.RatingDto> result = adminService.getAllRatings(0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getModerationStatus()).isEqualTo("HIDDEN");
    }
}
