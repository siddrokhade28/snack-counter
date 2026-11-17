package com.snackcounter.service;

import static com.snackcounter.model.Office.OSLO;
import static com.snackcounter.support.SnackFixture.RECEIPT_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snackcounter.exception.NotAllowedException;
import com.snackcounter.exception.RefillConflictException;
import com.snackcounter.model.Refill;
import com.snackcounter.model.RefillStatus;
import com.snackcounter.support.SnackFixture;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Rule: a refill nobody completes is handed to someone else, so the box never sits empty for days. */
class HandoverTest {

    private final SnackFixture f = new SnackFixture();
    private Refill refill;

    @BeforeEach
    void ingridIsAskedFirst() {
        f.checkIn(OSLO, "E100", "E101");
        refill = f.service.reportEmpty(OSLO, "E102");
        assertThat(refill.assignee()).isEqualTo("E100"); // tie on fairness -> lowest id
    }

    @Test
    void noAnswerWithinTwoHoursOffersItToTheNextPerson() {
        f.clock.advance(Duration.ofHours(2));
        f.service.escalateOverdue();

        assertThat(refill.status()).isEqualTo(RefillStatus.OFFERED);
        assertThat(refill.assignee()).isEqualTo("E101");
        assertThat(refill.handedOff()).containsExactly("E100");
        assertThat(f.chat.textsTo("E100")).last().asString().contains("offered to someone else");
        assertThat(f.chat.textsTo("E101")).singleElement().asString().contains("commit " + refill.id());
    }

    @Test
    void nothingHappensBeforeTheDeadline() {
        f.clock.advance(Duration.ofHours(2).minusSeconds(1));
        f.service.escalateOverdue();

        assertThat(refill.assignee()).isEqualTo("E100");
        assertThat(refill.handedOff()).isEmpty();
    }

    @Test
    void noReceiptWithinADayHandsItOnAndTellsTheLatePersonNotToBuy() {
        f.service.commit(refill.id(), "E100");

        f.clock.advance(Duration.ofDays(1));
        f.checkIn(OSLO, "E101"); // presence is per day: Lars is in again tomorrow
        f.service.escalateOverdue();

        assertThat(refill.status()).isEqualTo(RefillStatus.OFFERED);
        assertThat(refill.assignee()).isEqualTo("E101");
        assertThat(f.chat.textsTo("E100")).last().asString().contains("Please don't buy");
        assertThatThrownBy(() -> f.service.uploadReceipt(refill.id(), "E100", f.money("450", OSLO), f.today(OSLO),
                List.of(), RECEIPT_IMAGE)).isInstanceOf(RefillConflictException.class);
    }

    @Test
    void theHandedOffPersonCannotGrabItBackWhileSomeoneElseIsAsked() {
        f.clock.advance(Duration.ofHours(2));
        f.service.escalateOverdue();

        assertThatThrownBy(() -> f.service.commit(refill.id(), "E100"))
                .isInstanceOf(RefillConflictException.class).hasMessageContaining("handed to someone else");
    }

    @Test
    void decliningHandsItOnStraightAway() {
        f.service.decline(refill.id(), "E100");

        assertThat(refill.assignee()).isEqualTo("E101");
        assertThat(refill.handedOff()).containsExactly("E100");
    }

    @Test
    void onlyTheAssigneeCanDecline() {
        assertThatThrownBy(() -> f.service.decline(refill.id(), "E101")).isInstanceOf(NotAllowedException.class);
    }

    @Test
    void whenEveryoneHasPassedItIsOpenedUpToAnyoneAtTheOffice() {
        f.clock.advance(Duration.ofHours(2));
        f.service.escalateOverdue(); // E100 -> E101
        f.clock.advance(Duration.ofHours(2));
        f.service.escalateOverdue(); // E101 -> nobody left

        assertThat(refill.status()).isEqualTo(RefillStatus.NEEDS_VOLUNTEER);
        assertThat(refill.assignee()).isNull();
        assertThat(f.chat.textsTo("#snacks-oslo")).last().asString().contains("Anyone at the office can pick it up");

        f.service.commit(refill.id(), "E100"); // now even an earlier person may take it
        assertThat(refill.status()).isEqualTo(RefillStatus.COMMITTED);
    }

    @Test
    void runningTheEscalationTwiceDoesNotSkipAPerson() {
        f.checkIn(OSLO, "E102");
        f.clock.advance(Duration.ofHours(2));

        f.service.escalateOverdue();
        f.service.escalateOverdue();

        assertThat(refill.assignee()).isEqualTo("E101");
        assertThat(refill.handedOff()).containsExactly("E100");
    }
}
