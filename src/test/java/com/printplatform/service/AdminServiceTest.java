package com.printplatform.service;

import com.printplatform.dto.AdminCodeDto;
import com.printplatform.dto.AdminListingDto;
import com.printplatform.dto.AuthResponse;
import com.printplatform.dto.UserSummaryDto;
import com.printplatform.model.AdminCode;
import com.printplatform.model.Listing;
import com.printplatform.model.ListingStatus;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.AdminCodeRepository;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.UserRepository;
import com.printplatform.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(codeRepository, userRepository, listingRepository, jwtService);
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
}
