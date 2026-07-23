package com.printplatform.service;

import com.printplatform.dto.CostSettingsRequest;
import com.printplatform.dto.FinanceSummaryDto;
import com.printplatform.dto.MonthBucketDto;
import com.printplatform.dto.OverdueAlertDto;
import com.printplatform.dto.PipelineEntryDto;
import com.printplatform.dto.RecurringCostRequest;
import com.printplatform.model.*;
import com.printplatform.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    private static final List<OfferStatus> PIPELINE_ORDER = List.of(
            OfferStatus.PENDING, OfferStatus.SELECTED, OfferStatus.PAID,
            OfferStatus.PRINTING, OfferStatus.SHIPPED, OfferStatus.DELIVERED,
            OfferStatus.REJECTED);

    private static final int OVERDUE_DAYS = 7;

    @Transactional(readOnly = true)
    public List<PipelineEntryDto> getPipeline(User seller) {
        List<Offer> offers = offerRepository.findByUserId(seller.getId());
        List<PipelineEntryDto> result = new ArrayList<>();
        for (OfferStatus status : PIPELINE_ORDER) {
            List<Offer> matching = offers.stream()
                    .filter(o -> o.getStatus() == status)
                    .toList();
            BigDecimal value = status == OfferStatus.REJECTED
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : matching.stream()
                        .map(Offer::getPrice)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP);
            result.add(new PipelineEntryDto(status.name(), matching.size(), value));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<OverdueAlertDto> getAlerts(User seller) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return offerRepository.findByUserId(seller.getId()).stream()
                .filter(o -> o.getStatus() == OfferStatus.SELECTED)
                .filter(o -> paymentRepository.findByOfferId(o.getId())
                        .map(p -> p.getStatus() == PaymentStatus.REFUNDED)
                        .orElse(true))
                .map(o -> {
                    java.time.LocalDateTime since =
                            o.getSelectedAt() != null ? o.getSelectedAt() : o.getCreatedAt();
                    java.time.LocalDateTime sinceSec = since.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
                    java.time.LocalDateTime nowSec = now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
                    if (!sinceSec.isBefore(nowSec.minusDays(OVERDUE_DAYS))) {
                        return null;
                    }
                    long days = java.time.temporal.ChronoUnit.DAYS.between(since, now);
                    return new OverdueAlertDto(
                            o.getId(),
                            o.getListing().getId(),
                            o.getListing().getTitle(),
                            displayNameService.resolve(o.getListing().getUser()),
                            o.getPrice(),
                            days);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(OverdueAlertDto::getDaysOverdue).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecurringCost> getCosts(User seller) {
        return recurringCostRepository.findBySellerId(seller.getId());
    }

    private void validateDates(RecurringCostRequest req) {
        if (req.getStartDate() != null && req.getEndDate() != null
                && req.getEndDate().isBefore(req.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Data zakończenia nie może być wcześniejsza niż data rozpoczęcia");
        }
    }

    @Transactional
    public RecurringCost createCost(User seller, RecurringCostRequest req) {
        validateDates(req);
        RecurringCost cost = new RecurringCost();
        cost.setSeller(seller);
        cost.setName(req.getName());
        cost.setMonthlyAmount(req.getMonthlyAmount());
        cost.setStartDate(req.getStartDate() != null ? req.getStartDate() : LocalDate.now());
        cost.setEndDate(req.getEndDate());
        return recurringCostRepository.save(cost);
    }

    private RecurringCost ownedCost(User seller, UUID id) {
        RecurringCost cost = recurringCostRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!cost.getSeller().getId().equals(seller.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return cost;
    }

    @Transactional
    public RecurringCost updateCost(User seller, UUID id, RecurringCostRequest req) {
        validateDates(req);
        RecurringCost cost = ownedCost(seller, id);
        cost.setName(req.getName());
        cost.setMonthlyAmount(req.getMonthlyAmount());
        if (req.getStartDate() != null) {
            cost.setStartDate(req.getStartDate());
        }
        if (req.getEndDate() != null && req.getEndDate().isBefore(cost.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Data zakończenia nie może być wcześniejsza niż data rozpoczęcia");
        }
        cost.setEndDate(req.getEndDate());
        return recurringCostRepository.save(cost);
    }

    @Transactional
    public void deleteCost(User seller, UUID id) {
        recurringCostRepository.delete(ownedCost(seller, id));
    }

    @Transactional
    public SellerCostSettings updateSettings(User seller, CostSettingsRequest req) {
        SellerCostSettings s = getOrCreateSettings(seller);
        s.setFilamentPricePerKg(req.getFilamentPricePerKg());
        s.setCostPerPrintHour(req.getCostPerPrintHour());
        return settingsRepository.save(s);
    }
}
