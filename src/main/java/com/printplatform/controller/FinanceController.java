package com.printplatform.controller;

import com.printplatform.dto.*;
import com.printplatform.model.RecurringCost;
import com.printplatform.model.SellerCostSettings;
import com.printplatform.model.User;
import com.printplatform.service.FinanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @GetMapping("/summary")
    public FinanceSummaryDto getSummary(@AuthenticationPrincipal User seller) {
        return financeService.getSummary(seller);
    }

    @GetMapping("/pipeline")
    public List<PipelineEntryDto> getPipeline(@AuthenticationPrincipal User seller) {
        return financeService.getPipeline(seller);
    }

    @GetMapping("/alerts")
    public List<OverdueAlertDto> getAlerts(@AuthenticationPrincipal User seller) {
        return financeService.getAlerts(seller);
    }

    @GetMapping("/costs")
    public List<RecurringCost> getCosts(@AuthenticationPrincipal User seller) {
        return financeService.getCosts(seller);
    }

    @PostMapping("/costs")
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringCost createCost(@Valid @RequestBody RecurringCostRequest request,
                                    @AuthenticationPrincipal User seller) {
        return financeService.createCost(seller, request);
    }

    @PutMapping("/costs/{id}")
    public RecurringCost updateCost(@PathVariable UUID id,
                                    @Valid @RequestBody RecurringCostRequest request,
                                    @AuthenticationPrincipal User seller) {
        return financeService.updateCost(seller, id, request);
    }

    @DeleteMapping("/costs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCost(@PathVariable UUID id, @AuthenticationPrincipal User seller) {
        financeService.deleteCost(seller, id);
    }

    @GetMapping("/settings")
    public SellerCostSettings getSettings(@AuthenticationPrincipal User seller) {
        return financeService.getOrCreateSettings(seller);
    }

    @PutMapping("/settings")
    public SellerCostSettings updateSettings(@Valid @RequestBody CostSettingsRequest request,
                                             @AuthenticationPrincipal User seller) {
        return financeService.updateSettings(seller, request);
    }
}
