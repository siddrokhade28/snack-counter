package com.snackcounter.config;

import com.snackcounter.model.Money;
import com.snackcounter.model.Office;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Every tunable rule in one place, bound from the {@code snack.*} keys in application.yml. */
@ConfigurationProperties(prefix = "snack")
public record SnackProperties(
        Duration acceptTimeout,
        Duration purchaseTimeout,
        Duration fairnessWindow,
        Map<Office, OfficeSettings> offices,
        Report report,
        List<VolunteerSeed> volunteers) {

    /** Fails at startup with a clear message, instead of a 500 later, when the configuration can't work. */
    public SnackProperties {
        volunteers = volunteers == null ? List.of() : List.copyOf(volunteers);
        requirePositive(acceptTimeout, "snack.accept-timeout");
        requirePositive(purchaseTimeout, "snack.purchase-timeout");
        requirePositive(fairnessWindow, "snack.fairness-window");
        if (report == null || report.currency() == null || report.rates() == null) {
            throw new IllegalArgumentException("snack.report.currency and snack.report.rates are required");
        }
        for (Office office : Office.values()) {
            OfficeSettings settings = offices == null ? null : offices.get(office);
            if (settings == null) {
                throw new IllegalArgumentException("Missing snack.offices." + office);
            }
            for (Currency currency : List.of(settings.currency(), report.currency())) {
                if (!report.rates().containsKey(currency)) {
                    throw new IllegalArgumentException("Missing snack.report.rates." + currency + " (needed for " + office + ")");
                }
            }
        }
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be a positive duration");
        }
    }

    public OfficeSettings office(Office office) {
        OfficeSettings settings = offices.get(office);
        if (settings == null) {
            throw new IllegalStateException("No snack.offices." + office + " configuration");
        }
        return settings;
    }

    /**
     * @param typicalMin smallest normal purchase; below this much budget left, a refill needs an admin's OK
     * @param typicalMax largest normal purchase; the spending cap a volunteer is given never exceeds it
     * @param admins     employee ids who may approve over-budget refills for this office
     */
    public record OfficeSettings(Currency currency, BigDecimal monthlyBudget, BigDecimal typicalMin,
                                 BigDecimal typicalMax, List<String> admins) {

        public OfficeSettings {
            admins = admins == null ? List.of() : List.copyOf(admins);
            if (currency == null || monthlyBudget == null || typicalMin == null || typicalMax == null) {
                throw new IllegalArgumentException("Each office needs currency, monthly-budget, typical-min and typical-max");
            }
            if (monthlyBudget.signum() <= 0 || typicalMin.signum() <= 0 || typicalMin.compareTo(typicalMax) > 0) {
                throw new IllegalArgumentException("Need monthly-budget > 0 and 0 < typical-min <= typical-max (" + currency + ")");
            }
            if (admins.isEmpty()) {
                throw new IllegalArgumentException("Each office needs at least one admin, or over-budget refills get stuck");
            }
        }

        public Money budget() {
            return Money.of(monthlyBudget, currency);
        }

        public Money minPurchase() {
            return Money.of(typicalMin, currency);
        }

        public Money maxPurchase() {
            return Money.of(typicalMax, currency);
        }
    }

    /** @param rates 1 unit of the key currency = N units of the report currency (simulated, fixed). */
    public record Report(Currency currency, Map<Currency, BigDecimal> rates) {}

    public record VolunteerSeed(String employeeId, String name, Office homeOffice) {}
}
