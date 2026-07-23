package com.printplatform.service;

import com.printplatform.dto.CostSettingsRequest;
import com.printplatform.dto.FinanceSummaryDto;
import com.printplatform.dto.MonthBucketDto;
import com.printplatform.dto.OverdueAlertDto;
import com.printplatform.dto.PipelineEntryDto;
import com.printplatform.dto.RecurringCostRequest;
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

    @Test
    void getPipeline_groupsByStatusWithValues() {
        Offer p1 = offer(0, 0.0); p1.setStatus(OfferStatus.PENDING);
        Offer p2 = offer(0, 0.0); p2.setStatus(OfferStatus.PENDING);
        p2.setPrice(new BigDecimal("70.00"));
        Offer rejected = offer(0, 0.0); rejected.setStatus(OfferStatus.REJECTED);
        when(offerRepository.findByUserId(seller.getId()))
                .thenReturn(List.of(p1, p2, rejected));

        List<PipelineEntryDto> pipeline = financeService.getPipeline(seller);

        assertThat(pipeline).hasSize(7);
        PipelineEntryDto pending = pipeline.get(0);
        assertThat(pending.getStatus()).isEqualTo("PENDING");
        assertThat(pending.getCount()).isEqualTo(2);
        assertThat(pending.getValue()).isEqualByComparingTo("120.00");
        PipelineEntryDto rej = pipeline.get(6);
        assertThat(rej.getStatus()).isEqualTo("REJECTED");
        assertThat(rej.getCount()).isEqualTo(1);
        assertThat(rej.getValue()).isEqualByComparingTo("0.00");
    }

    private Offer selectedOffer(LocalDateTime selectedAt, LocalDateTime createdAt) {
        Offer o = offer(0, 0.0);
        o.setStatus(OfferStatus.SELECTED);
        o.setSelectedAt(selectedAt);
        o.setCreatedAt(createdAt);
        Listing listing = new Listing();
        listing.setId(UUID.randomUUID());
        listing.setTitle("Obudowa czujnika");
        User buyer = new User();
        buyer.setId(UUID.randomUUID());
        listing.setUser(buyer);
        o.setListing(listing);
        return o;
    }

    @Test
    void getAlerts_overdueOnlyAfterSevenFullDays() {
        LocalDateTime now = LocalDateTime.now();
        Offer overdue = selectedOffer(now.minusDays(8), now.minusDays(30));
        Offer fresh = selectedOffer(now.minusDays(3), now.minusDays(30));
        Offer exactlySeven = selectedOffer(now.minusDays(7), now.minusDays(30));
        when(offerRepository.findByUserId(seller.getId()))
                .thenReturn(List.of(overdue, fresh, exactlySeven));
        when(paymentRepository.findByOfferId(any())).thenReturn(Optional.empty());
        when(displayNameService.resolve(any(User.class))).thenReturn("Swift Maker");

        List<OverdueAlertDto> alerts = financeService.getAlerts(seller);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getOfferId()).isEqualTo(overdue.getId());
        assertThat(alerts.get(0).getDaysOverdue()).isEqualTo(8);
        assertThat(alerts.get(0).getBuyerName()).isEqualTo("Swift Maker");
        assertThat(alerts.get(0).getListingTitle()).isEqualTo("Obudowa czujnika");
    }

    @Test
    void getAlerts_nullSelectedAtFallsBackToCreatedAt() {
        LocalDateTime now = LocalDateTime.now();
        Offer legacy = selectedOffer(null, now.minusDays(10));
        when(offerRepository.findByUserId(seller.getId())).thenReturn(List.of(legacy));
        when(paymentRepository.findByOfferId(legacy.getId())).thenReturn(Optional.empty());
        when(displayNameService.resolve(any(User.class))).thenReturn("Swift Maker");

        List<OverdueAlertDto> alerts = financeService.getAlerts(seller);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDaysOverdue()).isEqualTo(10);
    }

    @Test
    void getAlerts_excludedWhenPaymentExists() {
        LocalDateTime now = LocalDateTime.now();
        Offer withPayment = selectedOffer(now.minusDays(9), now.minusDays(30));
        Payment held = payment(PaymentStatus.HELD, new BigDecimal("10.00"), now, null, withPayment);
        when(offerRepository.findByUserId(seller.getId())).thenReturn(List.of(withPayment));
        when(paymentRepository.findByOfferId(withPayment.getId())).thenReturn(Optional.of(held));

        assertThat(financeService.getAlerts(seller)).isEmpty();
    }

    @Test
    void getAlerts_refundedPaymentStillCountsAsUnpaid() {
        LocalDateTime now = LocalDateTime.now();
        Offer refundedOffer = selectedOffer(now.minusDays(9), now.minusDays(30));
        Payment refunded = payment(PaymentStatus.REFUNDED, new BigDecimal("10.00"), now, null, refundedOffer);
        when(offerRepository.findByUserId(seller.getId())).thenReturn(List.of(refundedOffer));
        when(paymentRepository.findByOfferId(refundedOffer.getId())).thenReturn(Optional.of(refunded));
        when(displayNameService.resolve(any(User.class))).thenReturn("Swift Maker");

        assertThat(financeService.getAlerts(seller)).hasSize(1);
    }

    private RecurringCost existingCost(User owner) {
        RecurringCost c = new RecurringCost();
        c.setId(UUID.randomUUID());
        c.setSeller(owner);
        c.setName("Licencja CAD");
        c.setMonthlyAmount(new BigDecimal("45.00"));
        c.setStartDate(LocalDate.now().minusMonths(2));
        return c;
    }

    @Test
    void createCost_defaultsStartDateToToday() {
        RecurringCostRequest req = new RecurringCostRequest();
        req.setName("Prąd");
        req.setMonthlyAmount(new BigDecimal("60.00"));
        when(recurringCostRepository.save(any(RecurringCost.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RecurringCost created = financeService.createCost(seller, req);

        assertThat(created.getStartDate()).isEqualTo(LocalDate.now());
        assertThat(created.getSeller()).isEqualTo(seller);
    }

    @Test
    void createCost_rejectsEndBeforeStart() {
        RecurringCostRequest req = new RecurringCostRequest();
        req.setName("Prąd");
        req.setMonthlyAmount(new BigDecimal("60.00"));
        req.setStartDate(LocalDate.now());
        req.setEndDate(LocalDate.now().minusDays(1));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> financeService.createCost(seller, req))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void updateCost_forbiddenForForeignCost() {
        User other = new User();
        other.setId(UUID.randomUUID());
        RecurringCost foreign = existingCost(other);
        when(recurringCostRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        RecurringCostRequest req = new RecurringCostRequest();
        req.setName("X");
        req.setMonthlyAmount(BigDecimal.ONE);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> financeService.updateCost(seller, foreign.getId(), req))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void deleteCost_notFoundForUnknownId() {
        UUID unknown = UUID.randomUUID();
        when(recurringCostRepository.findById(unknown)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> financeService.deleteCost(seller, unknown))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void updateSettings_overwritesBothValues() {
        when(settingsRepository.findBySellerId(seller.getId())).thenReturn(Optional.of(settings));
        when(settingsRepository.save(any(SellerCostSettings.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        CostSettingsRequest req = new CostSettingsRequest();
        req.setFilamentPricePerKg(new BigDecimal("95.00"));
        req.setCostPerPrintHour(new BigDecimal("2.20"));

        SellerCostSettings updated = financeService.updateSettings(seller, req);

        assertThat(updated.getFilamentPricePerKg()).isEqualByComparingTo("95.00");
        assertThat(updated.getCostPerPrintHour()).isEqualByComparingTo("2.20");
    }

    @Test
    void updateCost_rejectsEndBeforeKeptStartDate() {
        RecurringCost mine = existingCost(seller); // startDate = now minus 2 months
        when(recurringCostRepository.findById(mine.getId())).thenReturn(Optional.of(mine));
        RecurringCostRequest req = new RecurringCostRequest();
        req.setName("Licencja CAD");
        req.setMonthlyAmount(new BigDecimal("45.00"));
        req.setStartDate(null);
        req.setEndDate(LocalDate.now().minusMonths(3)); // before kept startDate

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> financeService.updateCost(seller, mine.getId(), req))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("400");
    }
}
