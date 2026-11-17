package com.snackcounter.service;

import static com.snackcounter.model.Office.BENGALURU;
import static com.snackcounter.model.Office.OSLO;
import static com.snackcounter.support.SnackFixture.RECEIPT_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snackcounter.client.SimulatedHrSystem.StoredClaim;
import com.snackcounter.exception.NotAllowedException;
import com.snackcounter.exception.RefillConflictException;
import com.snackcounter.exception.RefillNotFoundException;
import com.snackcounter.model.Refill;
import com.snackcounter.model.RefillStatus;
import com.snackcounter.support.SnackFixture;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The refill lifecycle from "the box is empty" to "the volunteer has their money back". */
class RefillLifecycleTest {

    private final SnackFixture f = new SnackFixture();

    @Test
    void aRefillGoesFromEmptyToReimbursed() {
        f.checkIn(OSLO, "E100");

        Refill refill = f.service.reportEmpty(OSLO, "someone-not-a-volunteer");
        assertThat(refill.status()).isEqualTo(RefillStatus.OFFERED);
        assertThat(refill.assignee()).isEqualTo("E100");
        assertThat(f.chat.textsTo("E100")).singleElement().asString()
                .contains("commit " + refill.id()).contains("Spend up to 1200.00 NOK")
                .contains("before 16 Nov 11:00 (Oslo time)"); // 08:00 UTC + 2 hours, shown in Oslo time

        f.service.commit(refill.id(), "E100");
        assertThat(refill.status()).isEqualTo(RefillStatus.COMMITTED);
        assertThat(f.chat.textsTo("#snacks-oslo")).anySatisfy(t -> assertThat(t).contains("Ingrid is refilling"));

        f.service.uploadReceipt(refill.id(), "E100", f.money("450", OSLO), f.today(OSLO), List.of("chips"), RECEIPT_IMAGE);
        assertThat(refill.status()).isEqualTo(RefillStatus.CLAIM_SUBMITTED);
        StoredClaim claim = f.hr.claims().getFirst();
        assertThat(claim.request().employeeId()).isEqualTo("E100");
        assertThat(claim.request().amount()).isEqualTo(f.money("450", OSLO));
        assertThat(claim.request().description()).contains("refill #" + refill.id()).contains("OSLO");
        assertThat(claim.request().receiptImage()).isEqualTo(RECEIPT_IMAGE);

        f.hr.approve(refill.claimId());
        f.service.syncClaims();
        assertThat(refill.status()).isEqualTo(RefillStatus.REIMBURSED);
        assertThat(f.chat.textsTo("E100")).last().asString().contains("HR approved");
    }

    @Test
    void aSecondEmptyReportJoinsTheOpenRefillSoNobodyBuysTwice() {
        f.checkIn(OSLO, "E100");

        Refill first = f.service.reportEmpty(OSLO, "E101");
        Refill second = f.service.reportEmpty(OSLO, "E102");

        assertThat(second).isSameAs(first);
        assertThat(f.refills.all()).hasSize(1);
        assertThat(f.chat.textsTo("E102")).singleElement().asString().contains("already being handled");
    }

    @Test
    void onceTheBoxIsRefilledTheNextEmptyReportStartsANewRefill() {
        Refill first = f.buy(OSLO, "E100", "450");

        Refill next = f.service.reportEmpty(OSLO, "E101");

        assertThat(next.id()).isNotEqualTo(first.id());
    }

    @Test
    void onlyTheVolunteerWhoCommittedCanUploadTheReceipt() {
        f.checkIn(OSLO, "E100");
        Refill refill = f.service.reportEmpty(OSLO, "E100");
        f.service.commit(refill.id(), "E100");

        assertThatThrownBy(() -> f.service.uploadReceipt(refill.id(), "E101", f.money("450", OSLO), f.today(OSLO),
                List.of(), RECEIPT_IMAGE))
                .isInstanceOf(NotAllowedException.class).hasMessageContaining("assigned to Ingrid");
    }

    @Test
    void aReceiptMustBeInTheOfficeCurrencyHaveAnImageAndNotBeFutureDated() {
        f.checkIn(OSLO, "E100");
        Refill refill = f.service.reportEmpty(OSLO, "E100");
        f.service.commit(refill.id(), "E100");
        long id = refill.id();

        assertThatThrownBy(() -> f.service.uploadReceipt(id, "E100", f.money("450", BENGALURU),
                f.today(OSLO), List.of(), RECEIPT_IMAGE)).hasMessageContaining("paid in NOK, not INR");
        assertThatThrownBy(() -> f.service.uploadReceipt(id, "E100", f.money("450", OSLO), f.today(OSLO), List.of(), new byte[0]))
                .hasMessageContaining("receipt image is required");
        assertThatThrownBy(() -> f.service.uploadReceipt(id, "E100", f.money("450", OSLO), f.today(OSLO).plusDays(1),
                List.of(), RECEIPT_IMAGE)).hasMessageContaining("future");
        assertThatThrownBy(() -> f.service.uploadReceipt(id, "E100", f.money("0", OSLO), f.today(OSLO), List.of(), RECEIPT_IMAGE))
                .hasMessageContaining("positive");
        assertThat(refill.status()).isEqualTo(RefillStatus.COMMITTED);
    }

    @Test
    void onlySomeoneCheckedInAtThatOfficeCanCommit() {
        f.checkIn(OSLO, "E100");
        Refill refill = f.service.reportEmpty(OSLO, "E102");

        assertThatThrownBy(() -> f.service.commit(refill.id(), "E101")) // Lars is working from home
                .isInstanceOf(NotAllowedException.class).hasMessageContaining("Lars is not checked in at OSLO today");
        f.checkIn(BENGALURU, "E200");
        assertThatThrownBy(() -> f.service.commit(refill.id(), "E200")) // Priya is at the other office
                .isInstanceOf(NotAllowedException.class);
        assertThat(refill.status()).isEqualTo(RefillStatus.OFFERED);
    }

    @Test
    void aReceiptDatedBeforeTheBoxWasReportedEmptyIsRefused() {
        f.checkIn(OSLO, "E100");
        Refill refill = f.service.reportEmpty(OSLO, "E102");
        f.service.commit(refill.id(), "E100");

        assertThatThrownBy(() -> f.service.uploadReceipt(refill.id(), "E100", f.money("450", OSLO),
                f.today(OSLO).minusDays(3), List.of(), RECEIPT_IMAGE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dated before the box was reported empty");
        assertThat(refill.status()).isEqualTo(RefillStatus.COMMITTED);
    }

    @Test
    void emptyItemsOnAReceiptAreDroppedNotAnError() {
        f.checkIn(OSLO, "E100");
        Refill refill = f.service.reportEmpty(OSLO, "E102");
        f.service.commit(refill.id(), "E100");

        f.service.uploadReceipt(refill.id(), "E100", f.money("450", OSLO), null,
                java.util.Arrays.asList(" chips ", null, "", "fruit"), RECEIPT_IMAGE);

        assertThat(refill.receipt().items()).containsExactly("chips", "fruit");
    }

    @Test
    void whenNobodyIsAvailableTheAdminsAreToldNotJustTheChannel() {
        Refill refill = f.service.reportEmpty(OSLO, "E102"); // nobody checked in

        assertThat(refill.status()).isEqualTo(RefillStatus.NEEDS_VOLUNTEER);
        assertThat(f.chat.textsTo("oslo-admin")).last().asString().contains("nobody on the rota is available");
    }

    @Test
    void hrDownWhileCheckingClaimsDoesNotStopTheChecksAndTheyCatchUpLater() {
        Refill oslo = f.buy(OSLO, "E100", "450");
        Refill bengaluru = f.buy(BENGALURU, "E200", "2500");
        f.hr.approve(oslo.claimId());
        f.hr.approve(bengaluru.claimId());
        f.hr.setAvailable(false);

        f.service.runScheduledChecks(); // must not throw: each refill is handled on its own
        assertThat(List.of(oslo.status(), bengaluru.status())).containsOnly(RefillStatus.CLAIM_SUBMITTED);

        f.hr.setAvailable(true);
        f.service.runScheduledChecks();
        assertThat(List.of(oslo.status(), bengaluru.status())).containsOnly(RefillStatus.REIMBURSED);
    }

    @Test
    void aClaimHrCouldNotTakeIsRetriedInsteadOfLost() {
        f.hr.setAvailable(false);
        Refill refill = f.buy(OSLO, "E100", "450");
        assertThat(refill.status()).isEqualTo(RefillStatus.READY_TO_CLAIM);
        assertThat(f.hr.claims()).isEmpty();

        f.hr.setAvailable(true);
        f.service.syncClaims();

        assertThat(refill.status()).isEqualTo(RefillStatus.CLAIM_SUBMITTED);
        assertThat(f.hr.claims()).hasSize(1);
    }

    @Test
    void aRejectedClaimIsFlaggedToTheVolunteerAndTheOfficeAdmin() {
        Refill refill = f.buy(OSLO, "E100", "450");

        f.hr.reject(refill.claimId());
        f.service.syncClaims();

        assertThat(refill.status()).isEqualTo(RefillStatus.CLAIM_REJECTED);
        assertThat(f.chat.textsTo("E100")).last().asString().contains("HR rejected");
        assertThat(f.chat.textsTo("oslo-admin")).last().asString().contains("Please follow up");
    }

    @Test
    void aPendingClaimStaysSubmittedUntilHrDecides() {
        Refill refill = f.buy(OSLO, "E100", "450");

        f.service.syncClaims();

        assertThat(refill.status()).isEqualTo(RefillStatus.CLAIM_SUBMITTED);
    }

    @Test
    void stepsOutOfOrderAreRejected() {
        Refill refill = f.buyAndReimburse(OSLO, "E100", "450");

        assertThatThrownBy(() -> f.service.commit(refill.id(), "E101")).isInstanceOf(RefillConflictException.class);
        assertThatThrownBy(() -> f.service.approveOverBudget(refill.id(), "oslo-admin"))
                .isInstanceOf(RefillConflictException.class);
        assertThatThrownBy(() -> f.service.find(999)).isInstanceOf(RefillNotFoundException.class);
    }

    @Test
    void theHistoryTellsTheWholeStory() {
        Refill refill = f.buyAndReimburse(OSLO, "E100", "450");

        assertThat(refill.history()).extracting(Refill.Event::text).containsExactly(
                "Box reported empty by E100",
                "Offered to E100 until 2026-11-16T10:00:00Z",
                "E100 committed to buy, receipt due 2026-11-17T08:00:00Z",
                "Receipt for 450.00 NOK uploaded by E100",
                "Claim HR-1 submitted to HR",
                "HR approved claim HR-1");
    }
}
