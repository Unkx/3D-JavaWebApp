package com.printplatform.controller;

import com.printplatform.model.Payment;
import com.printplatform.model.User;
import com.printplatform.service.PaymentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PutMapping("/{paymentId}/release")
    public Payment releasePayment(@PathVariable UUID paymentId,
                                  @AuthenticationPrincipal User currentUser) {
        return paymentService.releasePayment(paymentId);
    }
}
