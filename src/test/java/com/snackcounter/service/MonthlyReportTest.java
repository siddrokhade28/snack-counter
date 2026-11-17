package com.snackcounter.service;

import static com.snackcounter.model.Office.BENGALURU;
import static com.snackcounter.model.Office.OSLO;
import static com.snackcounter.support.SnackFixture.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snackcounter.dto.MonthlyReportResponse;
import com.snackcounter.dto.MonthlyReportResponse.ItemCount;
import com.snackcounter.dto.MonthlyReportResponse.OfficeLine;
import com.snackcounter.model.Money;
import com.snackcounter.model.Refill;
import com.snackcounter.support.SnackFixture;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Currency;
import org.junit.jupiter.api.Test;

/** Rule: the monthly report's totals are right across both currencies. */
class MonthlyReportTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final Currency NOK = Currency.getInstance("NOK");
    private static final Currency INR = Currency.getInstance("INR");

    private final SnackFixture f = new SnackFixture();

    @Test
    void eachOfficeIsInItsOwnCurrencyAndTheTotalIsInNok() {
        f.buy(OSLO, "E100", "450", "Chips", "bananas");
        f.buy(OSLO, "E101", "750", "chips ");
        f.buy(BENGALURU, "E200", "2500", "samosa", "CHIPS");
        f.buy(BENGALURU, "E201", "1000");

        MonthlyReportResponse report = f.reports().report(NOVEMBER);

        OfficeLine oslo = report.offices().get(0);
        assertThat(oslo.office()).isEqualTo(OSLO);
        assertThat(oslo.spent()).isEqualTo(Money.of("1200", "NOK"));
        assertThat(oslo.remaining()).isEqualTo(Money.of("4800", "NOK"));
        assertThat(oslo.refills()).isEqualTo(2);
        assertThat(oslo.spentInReportCurrency()).isEqualTo(Money.of("1200", "NOK"));

        OfficeLine bengaluru = report.offices().get(1);
        assertThat(bengaluru.spent()).isEqualTo(Money.of("3500", "INR"));
        assertThat(bengaluru.remaining()).isEqualTo(Money.of("11500", "INR"));
        assertThat(bengaluru.spentInReportCurrency()).isEqualTo(Money.of("420", "NOK")); // 3500 x 0.12

        assertThat(report.reportCurrency()).isEqualTo("NOK");
        assertThat(report.totalSpent()).isEqualTo(Money.of("1620", "NOK"));
        assertThat(report.totalBudget()).isEqualTo(Money.of("7800", "NOK")); // 6000 + 15000 x 0.12
        assertThat(report.totalRefills()).isEqualTo(4);
        assertThat(report.topItems()).containsExactly(
                new ItemCount("chips", 3), new ItemCount("bananas", 1), new ItemCount("samosa", 1));
    }

    @Test
    void moneyStillOwedToVolunteersIsShownAndDropsOnceHrPays() {
        Refill oslo = f.buy(OSLO, "E100", "450");
        f.buy(BENGALURU, "E200", "2500");

        assertThat(f.reports().report(NOVEMBER).owedToVolunteers()).isEqualTo(Money.of("750", "NOK")); // 450 + 300

        f.hr.approve(oslo.claimId());
        f.service.syncClaims();

        MonthlyReportResponse report = f.reports().report(NOVEMBER);
        assertThat(report.offices().get(0).owedToVolunteers()).isEqualTo(Money.of("0", "NOK"));
        assertThat(report.owedToVolunteers()).isEqualTo(Money.of("300", "NOK"));
    }

    @Test
    void otherMonthsAreLeftOut() {
        f.clock.set(Instant.parse("2026-10-20T08:00:00Z"));
        f.buy(OSLO, "E100", "999");
        f.clock.set(START);
        f.buy(OSLO, "E101", "450");

        assertThat(f.reports().report(NOVEMBER).totalSpent()).isEqualTo(Money.of("450", "NOK"));
        assertThat(f.reports().report(YearMonth.of(2026, 10)).totalSpent()).isEqualTo(Money.of("999", "NOK"));
    }

    @Test
    void eachReceiptIsConvertedAtTheRateForItsOwnPurchaseDate() {
        ExchangeRates rateChangesOnTheTenth = (from, to, on) -> {
            if (from.equals(to)) {
                return BigDecimal.ONE;
            }
            return on.isBefore(LocalDate.of(2026, 11, 10)) ? new BigDecimal("0.10") : new BigDecimal("0.12");
        };
        f.clock.set(Instant.parse("2026-11-05T08:00:00Z"));
        f.buy(BENGALURU, "E200", "1000"); // -> 100 NOK
        f.clock.set(START);
        f.buy(BENGALURU, "E201", "1000"); // -> 120 NOK

        MonthlyReportResponse report = f.reports(rateChangesOnTheTenth).report(NOVEMBER);

        assertThat(report.offices().get(1).spent()).isEqualTo(Money.of("2000", "INR"));
        assertThat(report.totalSpent()).isEqualTo(Money.of("220", "NOK"));
    }

    @Test
    void anEmptyMonthReportsZeroesNotErrors() {
        MonthlyReportResponse report = f.reports().report(NOVEMBER);

        assertThat(report.totalSpent()).isEqualTo(Money.zero(NOK));
        assertThat(report.totalRefills()).isZero();
        assertThat(report.topItems()).isEmpty();
    }

    @Test
    void configuredRatesWorkInBothDirections() {
        FixedExchangeRates rates = new FixedExchangeRates(f.properties);
        LocalDate any = LocalDate.of(2026, 11, 16);

        assertThat(rates.rate(INR, NOK, any)).isEqualByComparingTo("0.12");
        assertThat(rates.rate(NOK, INR, any)).isEqualByComparingTo(BigDecimal.ONE.divide(new BigDecimal("0.12"), MathContext.DECIMAL64));
        assertThat(rates.rate(NOK, NOK, any)).isEqualByComparingTo("1");
        assertThatThrownBy(() -> rates.rate(Currency.getInstance("USD"), NOK, any))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("USD");
    }
}
