package com.printplatform.controller;

import com.printplatform.dto.AdminActionDto;
import com.printplatform.dto.AdminCodeDto;
import com.printplatform.dto.AdminListingDto;
import com.printplatform.dto.AuthResponse;
import com.printplatform.dto.PageResponse;
import com.printplatform.dto.RedeemCodeRequest;
import com.printplatform.dto.RevenueSummaryDto;
import com.printplatform.dto.TrafficSummaryDto;
import com.printplatform.dto.UserSummaryDto;
import com.printplatform.model.User;
import com.printplatform.service.AdminService;
import com.printplatform.service.AnalyticsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AnalyticsService analyticsService;

    public AdminController(AdminService adminService, AnalyticsService analyticsService) {
        this.adminService = adminService;
        this.analyticsService = analyticsService;
    }

    /** List all listings (admin only). */
    @GetMapping("/listings")
    public List<AdminListingDto> listAllListings() {
        return adminService.listAllListings();
    }

    /** List all users (admin only). */
    @GetMapping("/users")
    public List<UserSummaryDto> listUsers() {
        return adminService.listUsers();
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

    /** Suspend a user's account (admin only). */
    @PutMapping("/users/{id}/suspend")
    public UserSummaryDto suspendUser(@PathVariable UUID id, @AuthenticationPrincipal User admin) {
        return adminService.suspendUser(admin, id);
    }

    /** Lift a user's suspension (admin only). */
    @PutMapping("/users/{id}/unsuspend")
    public UserSummaryDto unsuspendUser(@PathVariable UUID id, @AuthenticationPrincipal User admin) {
        return adminService.unsuspendUser(admin, id);
    }

    /** Hide a listing from the public feed without deleting it (admin only). */
    @PutMapping("/listings/{id}/hide")
    public AdminListingDto hideListing(@PathVariable UUID id, @AuthenticationPrincipal User admin) {
        return adminService.hideListing(admin, id);
    }

    /** Restore a hidden listing to the public feed (admin only). */
    @PutMapping("/listings/{id}/unhide")
    public AdminListingDto unhideListing(@PathVariable UUID id, @AuthenticationPrincipal User admin) {
        return adminService.unhideListing(admin, id);
    }

    /** Paged admin action history, newest first (admin only). */
    @GetMapping("/audit-log")
    public PageResponse<AdminActionDto> getAuditLog(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return adminService.getAuditLog(page, size);
    }

    /** Traffic summary: page views by day, top paths, API error/latency stats (admin only). */
    @GetMapping("/traffic")
    public TrafficSummaryDto getTraffic(@RequestParam(defaultValue = "7") int days) {
        return analyticsService.getTrafficSummary(days);
    }

    /** Revenue summary from realized payments (admin only). */
    @GetMapping("/revenue")
    public RevenueSummaryDto getRevenue(@RequestParam(defaultValue = "7") int days) {
        return adminService.getRevenueSummary(days);
    }
}
