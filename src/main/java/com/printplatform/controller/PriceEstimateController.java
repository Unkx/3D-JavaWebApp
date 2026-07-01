package com.printplatform.controller;

import com.printplatform.dto.PriceEstimateRequest;
import com.printplatform.dto.PriceEstimateResponse;
import com.printplatform.service.PriceEstimateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class PriceEstimateController {

    private final PriceEstimateService priceEstimateService;

    public PriceEstimateController(PriceEstimateService priceEstimateService) {
        this.priceEstimateService = priceEstimateService;
    }

    @PostMapping("/price-estimate")
    public PriceEstimateResponse getPriceEstimate(@Valid @RequestBody PriceEstimateRequest request) {
        return priceEstimateService.getEstimate(request);
    }
}
