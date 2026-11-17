package com.snackcounter.service;

import static com.snackcounter.model.Office.OSLO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snackcounter.exception.RefillConflictException;
import com.snackcounter.model.Office;
import com.snackcounter.model.Refill;
import com.snackcounter.model.RefillStatus;
import com.snackcounter.model.Volunteer;
import com.snackcounter.support.SnackFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** Rule: two people can never both commit to the same refill. */
class CommitConflictTest {

    private final SnackFixture f = new SnackFixture();

    @Test
    void aSecondPersonCannotCommitOnceSomeoneHas() {
        f.checkIn(OSLO, "E100", "E101");
        Refill refill = f.service.reportEmpty(OSLO, "E100");
        f.service.commit(refill.id(), "E100");

        assertThatThrownBy(() -> f.service.commit(refill.id(), "E101"))
                .isInstanceOf(RefillConflictException.class)
                .hasMessageContaining("already being bought by Ingrid");
        assertThat(refill.assignee()).isEqualTo("E100");
    }

    @Test
    void committingTwiceTellsYouYouAlreadyDid() {
        f.checkIn(OSLO, "E100");
        Refill refill = f.service.reportEmpty(OSLO, "E100");
        f.service.commit(refill.id(), "E100");

        assertThatThrownBy(() -> f.service.commit(refill.id(), "E100")).hasMessageContaining("already committed");
    }

    @Test
    void someoneCanStepInBeforeTheAskedPersonAnswersAndTheAskedPersonIsToldNotToBuy() {
        f.checkIn(OSLO, "E100", "E101");
        Refill refill = f.service.reportEmpty(OSLO, "E102");
        assertThat(refill.assignee()).isEqualTo("E100");

        f.service.commit(refill.id(), "E101");

        assertThat(refill.assignee()).isEqualTo("E101");
        assertThat(f.chat.textsTo("E100")).last().asString().contains("Lars picked up refill").contains("No need to buy");
        assertThatThrownBy(() -> f.service.commit(refill.id(), "E100")).isInstanceOf(RefillConflictException.class);
    }

    @Test
    void onlyRegisteredVolunteersCanCommit() {
        f.checkIn(OSLO, "E100");
        Refill refill = f.service.reportEmpty(OSLO, "E100");

        assertThatThrownBy(() -> f.service.commit(refill.id(), "stranger"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a registered volunteer");
    }

    /** Many volunteers press "I'll do it" at the same moment: exactly one wins, every other gets a conflict. */
    @RepeatedTest(20)
    void whenManyCommitAtTheSameTimeExactlyOneWins() throws Exception {
        List<String> racers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String id = "R" + i;
            f.volunteers.register(new Volunteer(id, "Racer " + i, Office.OSLO));
            racers.add(id);
        }
        Refill refill = f.service.reportEmpty(OSLO, "E100"); // nobody checked in -> open to anyone
        assertThat(refill.status()).isEqualTo(RefillStatus.NEEDS_VOLUNTEER);
        racers.forEach(id -> f.presence.checkIn(id, OSLO, f.today(OSLO))); // they arrive and see the open call

        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(racers.size());
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (String racer : racers) {
                Callable<Boolean> attempt = () -> {
                    go.await();
                    try {
                        f.service.commit(refill.id(), racer);
                        return true;
                    } catch (RefillConflictException lost) {
                        return false;
                    }
                };
                results.add(pool.submit(attempt));
            }
            go.countDown();

            long winners = 0;
            for (Future<Boolean> result : results) {
                winners += result.get(5, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertThat(winners).isEqualTo(1);
            assertThat(refill.status()).isEqualTo(RefillStatus.COMMITTED);
            assertThat(racers).contains(refill.assignee());
        } finally {
            pool.shutdownNow();
        }
    }

    /** Two reports of an empty box at the same moment still create only one refill. */
    @RepeatedTest(20)
    void simultaneousEmptyReportsCreateOneRefill() throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Refill>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                String reporter = "reporter-" + i;
                results.add(pool.submit(() -> {
                    go.await();
                    return f.service.reportEmpty(OSLO, reporter);
                }));
            }
            go.countDown();
            for (Future<Refill> result : results) {
                result.get(5, TimeUnit.SECONDS);
            }
            assertThat(f.refills.all()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
