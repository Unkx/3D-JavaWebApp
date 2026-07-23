package com.printplatform.service;

import com.printplatform.dto.FinanceSummaryDto;
import com.printplatform.dto.MonthBucketDto;
import com.printplatform.model.*;
import com.printplatform.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class FinanceService {

    private static final int MONTHS_BACK = 12;

    private final PaymentRepository paymentRepository;
    private final OfferRepository offerRepository;
    private final RecurringCostRepository recurringCostRepository;
    private final SellerCostSettingsRepository settingsRepository;
    private final UserDisplayNameService displayNameService;

    public FinanceService(PaymentRepository paymentRepository,
                          OfferRepository offerRepository,
                          RecurringCostRepository recurringCostRepository,
                          SellerCostSettingsRepository settingsRepository,
                          UserDisplayNameService displayNameService) {
        this.paymentRepository = paymentRepository;
        this.offerRepository = offerRepository;
        this.recurringCostRepository = recurringCostRepository;
        this.settingsRepository = settingsRepository;
        this.displayNameService = displayNameService;
    }

    @Transactional
    public SellerCostSettings getOrCreateSettings(User seller) {
        return settingsRepository.findBySellerId(seller.getId()).orElseGet(() -> {
            SellerCostSettings s = new SellerCostSettings();
            s.setSeller(seller);
            return settingsRepository.save(s);
        });
    }

    public BigDecimal estimatedCost(Offer offer, SellerCostSettings settings) {
        BigDecimal cost = BigDecimal.ZERO;
        if (offer.getFilamentGrams() != null) {
            cost = cost.add(BigDecimal.valueOf(offer.getFilamentGrams())
                    .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP)
                    .multiply(settings.getFilamentPricePerKg()));
        }
        if (offer.getPrintingTimeHours() != null) {
            cost = cost.add(BigDecimal.valueOf(offer.getPrintingTimeHours())
                    .multiply(settings.getCostPerPrintHour()));
        }
        return cost.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean activeInMonth(RecurringCost cost, YearMonth month) {
        boolean startedByMonthEnd = !cost.getStartDate().isAfter(month.atEndOfMonth());
        boolean notEndedBeforeMonth = cost.getEndDate() == null
                || !cost.getEndDate().isBefore(month.atDay(1));
        return startedByMonthEnd && notEndedBeforeMonth;
    }

    @Transactional(readOnly = true)
    public FinanceSummaryDto getSummary(User seller) {
        SellerCostSettings settings = settingsRepository.findBySellerId(seller.getId())
                .orElseGet(SellerCostSettings::new);
        List<Payment> payments = paymentRepository.findBySellerId(seller.getId());
        List<RecurringCost> recurring = recurringCostRepository.findBySellerId(seller.getId());

        YearMonth current = YearMonth.now();
        Map<YearMonth, BigDecimal> inflow = new HashMap<>();
        Map<YearMonth, BigDecimal> pending = new HashMap<>();
        Map<YearMonth, BigDecimal> orderCosts = new HashMap<>();
        BigDecimal totalReleased = BigDecimal.ZERO;
        BigDecimal totalHeld = BigDecimal.ZERO;

        for (Payment p : payments) {
            if (p.getStatus() == PaymentStatus.RELEASED && p.getReleasedAt() != null) {
                YearMonth m = YearMonth.from(p.getReleasedAt());
                inflow.merge(m, p.getContractorPrice(), BigDecimal::add);
                totalReleased = totalReleased.add(p.getContractorPrice());
            }
            if (p.getStatus() == PaymentStatus.HELD && p.getPaidAt() != null) {
                YearMonth m = YearMonth.from(p.getPaidAt());
                pending.merge(m, p.getContractorPrice(), BigDecimal::add);
                totalHeld = totalHeld.add(p.getContractorPrice());
            }
            if (p.getStatus() != PaymentStatus.REFUNDED && p.getStatus() != PaymentStatus.PENDING
                    && p.getPaidAt() != null && p.getOffer() != null) {
                YearMonth m = YearMonth.from(p.getPaidAt());
                orderCosts.merge(m, estimatedCost(p.getOffer(), settings), BigDecimal::add);
            }
        }

        List<MonthBucketDto> months = new ArrayList<>();
        for (int i = MONTHS_BACK - 1; i >= 0; i--) {
            YearMonth m = current.minusMonths(i);
            BigDecimal monthRecurring = recurring.stream()
                    .filter(c -> activeInMonth(c, m))
                    .map(RecurringCost::getMonthlyAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal in = inflow.getOrDefault(m, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal pend = pending.getOrDefault(m, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal costs = monthRecurring
                    .add(orderCosts.getOrDefault(m, BigDecimal.ZERO))
                    .setScale(2, RoundingMode.HALF_UP);
            months.add(new MonthBucketDto(m.toString(), in, pend, costs, in.subtract(costs)));
        }

        MonthBucketDto currentBucket = months.get(months.size() - 1);
        return new FinanceSummaryDto(
                totalReleased.setScale(2, RoundingMode.HALF_UP),
                totalHeld.setScale(2, RoundingMode.HALF_UP),
                currentBucket.getNet(),
                currentBucket.getCosts(),
                months);
    }
}
