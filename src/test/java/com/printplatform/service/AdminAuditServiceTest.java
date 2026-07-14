package com.printplatform.service;

import com.printplatform.model.AdminAction;
import com.printplatform.model.AdminActionType;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.AdminActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    @Mock
    private AdminActionRepository adminActionRepository;

    private AdminAuditService adminAuditService;

    @BeforeEach
    void setUp() {
        adminAuditService = new AdminAuditService(adminActionRepository);
    }

    @Test
    void log_savesActionWithAdminAndTargetDetails() {
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setEmail("admin@example.com");
        admin.setRole(Role.ADMIN);
        UUID targetId = UUID.randomUUID();

        when(adminActionRepository.save(org.mockito.ArgumentMatchers.any(AdminAction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        adminAuditService.log(admin, AdminActionType.HIDE_LISTING, "Listing", targetId, "spam report");

        ArgumentCaptor<AdminAction> captor = ArgumentCaptor.forClass(AdminAction.class);
        verify(adminActionRepository).save(captor.capture());
        AdminAction saved = captor.getValue();

        assertThat(saved.getAdminId()).isEqualTo(admin.getId());
        assertThat(saved.getAdminEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getActionType()).isEqualTo(AdminActionType.HIDE_LISTING);
        assertThat(saved.getTargetType()).isEqualTo("Listing");
        assertThat(saved.getTargetId()).isEqualTo(targetId);
        assertThat(saved.getDetails()).isEqualTo("spam report");
    }
}
