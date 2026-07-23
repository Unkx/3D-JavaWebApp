package com.printplatform.service;

import com.printplatform.dto.FinanceSummaryDto;
import com.printplatform.dto.MonthBucketDto;
import com.printplatform.model.*;
import com.printplatform.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OfferRepository offerRepository;
    @Mock private RecurringCostRepository recurringCostRepository;
    @Mock private SellerCostSettingsRepository settingsRepository;
    @Mock private UserDisplayNameService displayNameService;
    @InjectMocks private FinanceService financeService;

    private User seller;
    private SellerCostSettings settings;

    @BeforeEach
    void setUp() {
        seller = new User();
        seller.setId(UUID.randomUUID());
        settings = new SellerCostSettings();
        settings.setSeller(seller);
        settings.setFilamentPricePerKg(new BigDecimal("100.00"));
        settings.setCostPerPrintHour(new BigDecimal("2.00"));
    }

    private Offer offer(Integer grams, Double hours) {
        Offer o = new Offer();
        o.setId(UUID.randomUUID());
        o.setFilamentGrams(grams);
        o.setPrintingTimeHours(hours);
        o.setPrice(new BigDecimal("50.00"));
        return o;
    }

    private Payment payment(PaymentStatus status, BigDecimal contractorPrice,
                            LocalDateTime paidAt, LocalDateTime releasedAt, Offer offer) {
        Payment p = new Payment();
        p.setStatus(status);
        p.setContractorPrice(contractorPrice);
        p.setPaidAt(paidAt);
        p.setReleasedAt(releasedAt);
        p.setOffer(offer);
        p.setSeller(seller);
        return p;
    }

    @Test
    void estimatedCost_combinesFilamentAndHours() {
        // 500 g at 100 PLN/kg = 50.00; 3 h at 2 PLN/h = 6.00
        BigDecimal cost = financeService.estimatedCost(offer(500, 3.0), settings);
        assertThat(cost).isEqualByComparingTo("56.00");
    }

    @Test
    void estimatedCost_nullTermsContributeZero() {
        assertThat(financeService.estimatedCost(offer(null, 3.0), settings))
                .isEqualByComparingTo("6.00");
        assertThat(financeService.estimatedCost(offer(500, null), settings))
                .isEqualByComparingTo("50.00");
        assertThat(financeService.estimatedCost(offer(null, null), settings))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void getOrCreateSettings_createsDefaultsWhenAbsent() {
        when(settingsRepository.findBySellerId(seller.getId())).thenReturn(Optional.empty());
        when(settingsRepository.save(any(SellerCostSettings.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SellerCostSettings created = financeService.getOrCreateSettings(seller);

        assertThat(created.getFilamentPricePerKg()).isEqualByComparingTo("120.00");
        assertThat(created.getCostPerPrintHour()).isEqualByComparingTo("1.50");
        assertThat(created.getSeller()).isEqualTo(seller);
    }

    @Test
    void getSummary_bucketsReleasedByReleasedAtAndHeldByPaidAt() {
        LocalDateTime now = LocalDateTime.now();
        Offer o1 = offer(0, 0.0);
        Payment released = payment(PaymentStatus.RELEASED, new BigDecimal("200.00"),
                now.minusMonths(1), now.minusMonths(1), o1);
        Payment held = payment(PaymentStatus.HELD, new BigDecimal("80.00"), now, null, o1);
        Payment refunded = payment(PaymentStatus.REFUNDED, new BigDecimal("999.00"), now, null, o1);
        when(paymentRepository.findBySellerId(seller.getId()))
                .thenReturn(List.of(released, held, refunded));
        when(recurringCostRepository.findBySellerId(seller.getId())).thenReturn(List.of());
        when(settingsRepository.findBySellerId(seller.getId())).thenReturn(Optional.of(settings));

        FinanceSummaryDto dto = financeService.getSummary(seller);

        assertThat(dto.getMonths()).hasSize(12);
        MonthBucketDto lastMonth = dto.getMonths().get(10);
        MonthBucketDto currentMonth = dto.getMonths().get(11);
        assertThat(lastMonth.getInflow()).isEqualByComparingTo("200.00");
        assertThat(currentMonth.getPending()).isEqualByComparingTo("80.00");
        assertThat(dto.getTotalReleased()).isEqualByComparingTo("200.00");
        assertThat(dto.getTotalHeld()).isEqualByComparingTo("80.00");
        // REFUNDED excluded everywhere
        assertThat(currentMonth.getInflow()).isEqualByComparingTo("0.00");
    }

    @Test
    void getSummary_recurringCostActiveMonthBoundaries() {
        LocalDate firstOfCurrentMonth = LocalDate.now().withDayOfMonth(1);
        RecurringCost cost = new RecurringCost();
        cost.setSeller(seller);
        cost.setName("Prenumerata");
        cost.setMonthlyAmount(new BigDecimal("30.00"));
        // started last day of previous month, ended first day of current month:
        // active in BOTH months (no proration)
        cost.setStartDate(firstOfCurrentMonth.minusDays(1));
        cost.setEndDate(firstOfCurrentMonth);
        when(paymentRepository.findBySellerId(seller.getId())).thenReturn(List.of());
        when(recurringCostRepository.findBySellerId(seller.getId())).thenReturn(List.of(cost));
        when(settingsRepository.findBySellerId(seller.getId())).thenReturn(Optional.of(settings));

        FinanceSummaryDto dto = financeService.getSummary(seller);

        MonthBucketDto prev = dto.getMonths().get(10);
        MonthBucketDto curr = dto.getMonths().get(11);
        assertThat(prev.getCosts()).isEqualByComparingTo("30.00");
        assertThat(curr.getCosts()).isEqualByComparingTo("30.00");
        assertThat(curr.getNet()).isEqualByComparingTo("-30.00");
        assertThat(dto.getMonthCosts()).isEqualByComparingTo("30.00");
    }

    @Test
    void getSummary_orderCostsBucketedByPaidAt() {
        LocalDateTime now = LocalDateTime.now();
        Offer o = offer(1000, 1.0); // 100.00 + 2.00 = 102.00
        Payment paid = payment(PaymentStatus.HELD, new BigDecimal("150.00"), now, null, o);
        when(paymentRepository.findBySellerId(seller.getId())).thenReturn(List.of(paid));
        when(recurringCostRepository.findBySellerId(seller.getId())).thenReturn(List.of());
        when(settingsRepository.findBySellerId(seller.getId())).thenReturn(Optional.of(settings));

        FinanceSummaryDto dto = financeService.getSummary(seller);

        assertThat(dto.getMonths().get(11).getCosts()).isEqualByComparingTo("102.00");
    }
}
