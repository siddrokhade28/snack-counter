package com.snackcounter.service;

import static com.snackcounter.model.Office.BENGALURU;
import static com.snackcounter.model.Office.OSLO;
import static com.snackcounter.support.SnackFixture.RECEIPT_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snackcounter.exception.NotAllowedException;
import com.snackcounter.exception.RefillConflictException;
import com.snackcounter.model.Office;
import com.snackcounter.model.Refill;
import com.snackcounter.model.RefillStatus;
import com.snackcounter.support.SnackFixture;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rule: each office's monthly budget is checked in its own currency, by its own local calendar. */
class BudgetTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    private final SnackFixture f = new SnackFixture();

    @Test
    void spendingIsTrackedPerOfficeInItsOwnCurrency() {
        f.buy(OSLO, "E100", "450");
        f.buy(OSLO, "E101", "700");
        f.buy(BENGALURU, "E200", "2500");

        assertThat(f.budget.spent(OSLO, NOVEMBER)).isEqualTo(f.money("1150", OSLO));
        assertThat(f.budget.remaining(OSLO, NOVEMBER)).isEqualTo(f.money("4850", OSLO));
        assertThat(f.budget.spent(BENGALURU, NOVEMBER)).isEqualTo(f.money("2500", BENGALURU));
        assertThat(f.budget.remaining(BENGALURU, NOVEMBER)).isEqualTo(f.money("12500", BENGALURU));
    }

    @Test
    void aReceiptCountsInTheMonthItWasBoughtNotUploaded() {
        f.clock.set(Instant.parse("2026-10-31T19:00:00Z")); // 20:00 on 31 October in Oslo
        f.checkIn(OSLO, "E100");
        Refill refill = f.service.reportEmpty(OSLO, "E100");
        f.service.commit(refill.id(), "E100");
        f.clock.set(Instant.parse("2026-11-01T08:00:00Z")); // the receipt is uploaded the next morning, in November
        f.service.uploadReceipt(refill.id(), "E100", f.money("300", OSLO), LocalDate.of(2026, 10, 31), List.of(), RECEIPT_IMAGE);

        assertThat(f.budget.spent(OSLO, YearMonth.of(2026, 10))).isEqualTo(f.money("300", OSLO));
        assertThat(f.budget.spent(OSLO, NOVEMBER)).isEqualTo(f.money("0", OSLO));
    }

    @Test
    void aRejectedClaimDoesNotCountAgainstTheBudget() {
        Refill refill = f.buy(OSLO, "E100", "450");
        f.hr.reject(refill.claimId());
        f.service.syncClaims();

        assertThat(f.budget.spent(OSLO, NOVEMBER)).isEqualTo(f.money("0", OSLO));
    }

    @Test
    void theVolunteerIsToldToSpendNoMoreThanWhatIsLeft() {
        spend(OSLO, "1200", "1200", "1200", "1200", "400"); // 5200 of 6000 -> 800 left
        f.checkIn(OSLO, "E102");

        f.service.reportEmpty(OSLO, "E102");

        assertThat(f.chat.textsTo("E102")).last().asString().contains("Spend up to 800.00 NOK");
    }

    @Test
    void whenTheBudgetIsNearlyUsedUpNobodyBuysUntilAnAdminApproves() {
        spend(OSLO, "1200", "1200", "1200", "1200", "1000"); // 5800 -> 200 left, below the 300 minimum
        f.checkIn(OSLO, "E102");

        Refill refill = f.service.reportEmpty(OSLO, "E102");

        assertThat(refill.status()).isEqualTo(RefillStatus.AWAITING_BUDGET_APPROVAL);
        assertThat(refill.assignee()).isNull();
        assertThat(f.chat.textsTo("oslo-admin")).last().asString().contains("only 200.00 NOK");
        assertThatThrownBy(() -> f.service.commit(refill.id(), "E102")).isInstanceOf(RefillConflictException.class);
        assertThatThrownBy(() -> f.service.approveOverBudget(refill.id(), "blr-admin")).isInstanceOf(NotAllowedException.class);

        f.service.approveOverBudget(refill.id(), "oslo-admin");

        assertThat(refill.status()).isEqualTo(RefillStatus.OFFERED);
    }

    @Test
    void aReceiptAboveTheLimitTheVolunteerWasToldNeedsAnAdminEvenWithBudgetLeft() {
        f.checkIn(OSLO, "E100");
        Refill refill = f.service.reportEmpty(OSLO, "E102");
        f.service.commit(refill.id(), "E100"); // told: spend up to 1200.00 NOK (6000 left)

        f.service.uploadReceipt(refill.id(), "E100", f.money("3000", OSLO), f.today(OSLO), List.of(), RECEIPT_IMAGE);

        assertThat(refill.status()).isEqualTo(RefillStatus.AWAITING_ADMIN_APPROVAL);
        assertThat(f.hr.claims()).isEmpty();
    }

    @Test
    void aReceiptOverTheRemainingBudgetWaitsForAnAdminBeforeGoingToHr() {
        spend(OSLO, "1200", "1200", "1200", "1200", "700"); // 5500 -> 500 left
        f.checkIn(OSLO, "E102");
        Refill refill = f.service.reportEmpty(OSLO, "E102");
        f.service.commit(refill.id(), "E102");
        int claimsBefore = f.hr.claims().size();

        f.service.uploadReceipt(refill.id(), "E102", f.money("700", OSLO), f.today(OSLO), List.of(), RECEIPT_IMAGE);

        assertThat(refill.status()).isEqualTo(RefillStatus.AWAITING_ADMIN_APPROVAL);
        assertThat(f.hr.claims()).hasSize(claimsBefore);
        assertThat(f.chat.textsTo("oslo-admin")).last().asString().contains("over the spending limit (500.00 NOK");

        f.service.approveOverBudget(refill.id(), "oslo-admin");

        assertThat(refill.status()).isEqualTo(RefillStatus.CLAIM_SUBMITTED);
        assertThat(f.budget.remaining(OSLO, NOVEMBER)).isEqualTo(f.money("-200", OSLO));
    }

    @Test
    void bengaluruIsStillInNovemberAt2230LocalTime() {
        spend(BENGALURU, "3000", "3000", "3000", "3000", "2600"); // 14600 of 15000 -> 400 left, below 500
        f.clock.set(Instant.parse("2026-11-30T17:00:00Z")); // 22:30 in Bengaluru, 30 Nov
        f.checkIn(BENGALURU, "E201");

        Refill refill = f.service.reportEmpty(BENGALURU, "E201");

        assertThat(refill.status()).isEqualTo(RefillStatus.AWAITING_BUDGET_APPROVAL);
    }

    @Test
    void bengaluruGetsDecembersBudgetAfterLocalMidnightEvenThoughOsloIsStillInNovember() {
        spend(BENGALURU, "3000", "3000", "3000", "3000", "2600");
        f.clock.set(Instant.parse("2026-11-30T19:00:00Z")); // 00:30 on 1 Dec in Bengaluru, 20:00 on 30 Nov in Oslo
        f.checkIn(BENGALURU, "E201");

        Refill refill = f.service.reportEmpty(BENGALURU, "E201");

        assertThat(f.today(BENGALURU)).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(f.today(OSLO)).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(refill.status()).isEqualTo(RefillStatus.OFFERED);
        assertThat(f.chat.textsTo("E201")).last().asString().contains("Spend up to 3000.00 INR");
    }

    /** One completed refill per amount, today, rotating payers so nobody holds two open refills. */
    private void spend(Office office, String... amounts) {
        List<String> payers = office == OSLO ? List.of("E100", "E101") : List.of("E200", "E202");
        for (int i = 0; i < amounts.length; i++) {
            f.buy(office, payers.get(i % payers.size()), amounts[i]);
        }
    }
}
