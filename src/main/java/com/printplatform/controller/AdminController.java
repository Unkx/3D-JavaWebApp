package com.printplatform.controller;

import com.printplatform.dto.AdminCodeDto;
import com.printplatform.dto.AuthResponse;
import com.printplatform.dto.RedeemCodeRequest;
import com.printplatform.model.User;
import com.printplatform.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** Generate a new admin code (admin only). */
    @PostMapping("/codes")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCodeDto generateCode(@AuthenticationPrincipal User admin) {
        return adminService.generateCode(admin);
    }

    /** List all admin codes (admin only). */
    @GetMapping("/codes")
    public List<AdminCodeDto> listCodes() {
        return adminService.listCodes();
    }

    /** Redeem a code to become admin. Returns a fresh token reflecting the new role. */
    @PostMapping("/redeem")
    public AuthResponse redeem(@AuthenticationPrincipal User user,
                               @Valid @RequestBody RedeemCodeRequest request) {
        return adminService.redeemCode(user, request.getCode());
    }
}
