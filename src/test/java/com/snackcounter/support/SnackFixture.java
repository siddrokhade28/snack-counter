package com.snackcounter.support;

import com.snackcounter.client.SimulatedChatTool;
import com.snackcounter.client.SimulatedHrSystem;
import com.snackcounter.config.SnackProperties;
import com.snackcounter.config.SnackProperties.OfficeSettings;
import com.snackcounter.config.SnackProperties.Report;
import com.snackcounter.config.SnackProperties.VolunteerSeed;
import com.snackcounter.model.Money;
import com.snackcounter.model.Office;
import com.snackcounter.model.Refill;
import com.snackcounter.model.RefillStatus;
import com.snackcounter.repository.PresenceRepository;
import com.snackcounter.repository.RefillRepository;
import com.snackcounter.repository.VolunteerRepository;
import com.snackcounter.service.BudgetService;
import com.snackcounter.service.ExchangeRates;
import com.snackcounter.service.FixedExchangeRates;
import com.snackcounter.service.MonthlyReportService;
import com.snackcounter.service.PresenceService;
import com.snackcounter.service.RefillService;
import com.snackcounter.service.VolunteerSelector;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;

/**
 * The real objects wired by hand (no Spring), with the same rules as application.yml and a clock the
 * test controls. Starts on Monday 16 Nov 2026, 08:00 UTC (09:00 in Oslo, 13:30 in Bengaluru).
 */
public final class SnackFixture {

    public static final Instant START = Instant.parse("2026-11-16T08:00:00Z");
    public static final byte[] RECEIPT_IMAGE = {1, 2, 3};

    public final MutableClock clock = new MutableClock(START);
    public final SnackProperties properties = defaultProperties();
    public final VolunteerRepository volunteers = new VolunteerRepository(properties);
    public final PresenceService presence = new PresenceService(new PresenceRepository(), volunteers, clock);
    public final RefillRepository refills = new RefillRepository();
    public final SimulatedChatTool chat = new SimulatedChatTool(clock);
    public final SimulatedHrSystem hr = new SimulatedHrSystem();
    public final BudgetService budget = new BudgetService(refills, properties);
    public final VolunteerSelector selector = new VolunteerSelector(volunteers, presence, refills, properties, clock);
    public final RefillService service =
            new RefillService(refills, selector, budget, presence, volunteers, chat, hr, properties, clock);

    public static SnackProperties defaultProperties() {
        return new SnackProperties(
                Duration.ofHours(2),
                Duration.ofDays(1),
                Duration.ofDays(90),
                Map.of(
                        Office.OSLO, new OfficeSettings(Currency.getInstance("NOK"), new BigDecimal("6000"),
                                new BigDecimal("300"), new BigDecimal("1200"), List.of("oslo-admin")),
                        Office.BENGALURU, new OfficeSettings(Currency.getInstance("INR"), new BigDecimal("15000"),
                                new BigDecimal("500"), new BigDecimal("3000"), List.of("blr-admin"))),
                new Report(Currency.getInstance("NOK"),
                        Map.of(Currency.getInstance("NOK"), BigDecimal.ONE, Currency.getInstance("INR"), new BigDecimal("0.12"))),
                List.of(
                        new VolunteerSeed("E100", "Ingrid", Office.OSLO),
                        new VolunteerSeed("E101", "Lars", Office.OSLO),
                        new VolunteerSeed("E102", "Sofie", Office.OSLO),
                        new VolunteerSeed("E200", "Priya", Office.BENGALURU),
                        new VolunteerSeed("E201", "Arjun", Office.BENGALURU),
                        new VolunteerSeed("E202", "Meera", Office.BENGALURU)));
    }

    public MonthlyReportService reports() {
        return reports(new FixedExchangeRates(properties));
    }

    public MonthlyReportService reports(ExchangeRates rates) {
        return new MonthlyReportService(refills, budget, rates, properties, clock);
    }

    /** Puts these volunteers at this office today (office-local date). */
    public SnackFixture checkIn(Office office, String... employeeIds) {
        for (String id : employeeIds) {
            presence.checkIn(id, office, today(office));
        }
        return this;
    }

    public LocalDate today(Office office) {
        return office.today(clock);
    }

    public Money money(String amount, Office office) {
        return Money.of(new BigDecimal(amount), properties.office(office).currency());
    }

    /** Runs one refill all the way to a submitted HR claim, paid by {@code payer} today. */
    public Refill buy(Office office, String payer, String amount, String... items) {
        checkIn(office, payer);
        Refill refill = service.reportEmpty(office, payer);
        if (refill.status() != RefillStatus.COMMITTED) {
            service.commit(refill.id(), payer);
        }
        return service.uploadReceipt(refill.id(), payer, money(amount, office), today(office), List.of(items), RECEIPT_IMAGE);
    }

    /** Like {@link #buy} and HR has also paid it out. */
    public Refill buyAndReimburse(Office office, String payer, String amount) {
        Refill refill = buy(office, payer, amount);
        hr.approve(refill.claimId());
        service.syncClaims();
        return refill;
    }
}
