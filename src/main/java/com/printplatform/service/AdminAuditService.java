package com.printplatform.service;

import com.printplatform.model.AdminAction;
import com.printplatform.model.AdminActionType;
import com.printplatform.model.User;
import com.printplatform.repository.AdminActionRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Single write path for the admin accountability log — every admin moderation action goes through here. */
@Service
public class AdminAuditService {

    private final AdminActionRepository adminActionRepository;

    public AdminAuditService(AdminActionRepository adminActionRepository) {
        this.adminActionRepository = adminActionRepository;
    }

    public void log(User admin, AdminActionType actionType, String targetType, UUID targetId, String details) {
        AdminAction action = new AdminAction();
        action.setAdminId(admin.getId());
        action.setAdminEmail(admin.getEmail());
        action.setActionType(actionType);
        action.setTargetType(targetType);
        action.setTargetId(targetId);
        action.setDetails(details);
        adminActionRepository.save(action);
    }
}
