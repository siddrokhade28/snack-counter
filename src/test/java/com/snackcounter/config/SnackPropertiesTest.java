package com.snackcounter.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snackcounter.config.SnackProperties.OfficeSettings;
import com.snackcounter.config.SnackProperties.Report;
import com.snackcounter.model.Office;
import com.snackcounter.support.SnackFixture;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A configuration that can't work stops the app at startup with a clear message, not with 500s later. */
class SnackPropertiesTest {

    private static final Currency NOK = Currency.getInstance("NOK");
    private final SnackProperties valid = SnackFixture.defaultProperties();

    @Test
    void anOfficeCurrencyWithoutAnExchangeRateIsRejected() {
        Report noInrRate = new Report(NOK, Map.of(NOK, BigDecimal.ONE));

        assertThatThrownBy(() -> withReport(noInrRate))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Missing snack.report.rates.INR (needed for BENGALURU)");
    }

    @Test
    void anOfficeWithoutAnAdminIsRejected() {
        assertThatThrownBy(() -> new OfficeSettings(NOK, new BigDecimal("6000"), new BigDecimal("300"), new BigDecimal("1200"), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one admin");
    }

    @Test
    void impossibleAmountsAreRejected() {
        assertThatThrownBy(() -> new OfficeSettings(NOK, new BigDecimal("6000"), new BigDecimal("1500"), new BigDecimal("1200"), List.of("a")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("typical-min <= typical-max");
    }

    @Test
    void aMissingOfficeOrAZeroTimeoutIsRejected() {
        assertThatThrownBy(() -> new SnackProperties(valid.acceptTimeout(), valid.purchaseTimeout(), valid.fairnessWindow(),
                Map.of(Office.OSLO, valid.office(Office.OSLO)), valid.report(), valid.volunteers()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Missing snack.offices.BENGALURU");
        assertThatThrownBy(() -> new SnackProperties(Duration.ZERO, valid.purchaseTimeout(), valid.fairnessWindow(),
                valid.offices(), valid.report(), valid.volunteers()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("snack.accept-timeout must be a positive duration");
    }

    private SnackProperties withReport(Report report) {
        return new SnackProperties(valid.acceptTimeout(), valid.purchaseTimeout(), valid.fairnessWindow(),
                valid.offices(), report, valid.volunteers());
    }
}
