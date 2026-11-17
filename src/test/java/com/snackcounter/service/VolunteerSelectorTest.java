package com.snackcounter.service;

import static com.snackcounter.model.Office.BENGALURU;
import static com.snackcounter.model.Office.OSLO;
import static com.snackcounter.support.SnackFixture.START;
import static org.assertj.core.api.Assertions.assertThat;

import com.snackcounter.model.Refill;
import com.snackcounter.model.Volunteer;
import com.snackcounter.support.SnackFixture;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Rule: choosing who buys is fair, and only ever picks someone who is at that counter today. */
class VolunteerSelectorTest {

    private final SnackFixture f = new SnackFixture();

    private List<String> rankedAtOslo(Set<String> exclude) {
        return f.selector.ranked(OSLO, f.today(OSLO), exclude).stream().map(Volunteer::employeeId).toList();
    }

    private void daysAgo(int days) {
        f.clock.set(START.minus(Duration.ofDays(days)));
    }

    private void backToToday() {
        f.clock.set(START);
    }

    @Test
    void onlyPeopleCheckedInAtThatOfficeTodayAreAsked() {
        f.checkIn(OSLO, "E100");
        f.presence.checkIn("E102", OSLO, f.today(OSLO).minusDays(1)); // was in yesterday, not today
        // E101 never checked in: working from home or on holiday

        assertThat(rankedAtOslo(Set.of())).containsExactly("E100");
    }

    @Test
    void markingAwayRemovesYouForThatDay() {
        f.checkIn(OSLO, "E100", "E101");
        f.presence.markAway("E100", f.today(OSLO));

        assertThat(rankedAtOslo(Set.of())).containsExactly("E101");
    }

    @Test
    void aVisitorFromTheOtherOfficeCanBeAskedWhereTheyAreNotAtHome() {
        f.checkIn(OSLO, "E200"); // Priya (Bengaluru) is visiting Oslo this week

        assertThat(rankedAtOslo(Set.of())).containsExactly("E200");
        assertThat(f.selector.ranked(BENGALURU, f.today(BENGALURU), Set.of())).isEmpty();
    }

    @Test
    void whoeverDidFewestRecentRefillsIsAskedFirst() {
        daysAgo(10);
        f.buyAndReimburse(OSLO, "E100", "400");
        daysAgo(9);
        f.buyAndReimburse(OSLO, "E100", "400");
        daysAgo(8);
        f.buyAndReimburse(OSLO, "E101", "400");
        backToToday();
        f.checkIn(OSLO, "E100", "E101", "E102");

        assertThat(rankedAtOslo(Set.of())).containsExactly("E102", "E101", "E100");
    }

    @Test
    void refillsOlderThanTheFairnessWindowAreForgiven() {
        daysAgo(100);
        f.buyAndReimburse(OSLO, "E100", "400");
        daysAgo(10);
        f.buyAndReimburse(OSLO, "E101", "400");
        backToToday();
        f.checkIn(OSLO, "E100", "E101");

        assertThat(rankedAtOslo(Set.of())).containsExactly("E100", "E101");
    }

    @Test
    void someoneStillWaitingForTheirMoneyIsAskedAfterSomeoneWhoWasPaid() {
        daysAgo(10);
        f.buy(OSLO, "E100", "400"); // claim still with HR
        daysAgo(9);
        f.buyAndReimburse(OSLO, "E101", "400");
        backToToday();
        f.checkIn(OSLO, "E100", "E101");

        assertThat(rankedAtOslo(Set.of())).containsExactly("E101", "E100");
    }

    @Test
    void onATieWhoeverRefilledLongestAgoGoesFirst() {
        daysAgo(20);
        f.buyAndReimburse(OSLO, "E101", "400");
        daysAgo(5);
        f.buyAndReimburse(OSLO, "E100", "400");
        backToToday();
        f.checkIn(OSLO, "E100", "E101");

        assertThat(rankedAtOslo(Set.of())).containsExactly("E101", "E100");
    }

    @Test
    void peopleAlreadyHandedOffAreSkipped() {
        f.checkIn(OSLO, "E100", "E101");

        assertThat(rankedAtOslo(Set.of("E100"))).containsExactly("E101");
    }

    @Test
    void someoneBusyWithTheOtherOfficesRefillIsNotAskedTwice() {
        f.checkIn(BENGALURU, "E200");
        Refill bengaluru = f.service.reportEmpty(BENGALURU, "E201");
        assertThat(bengaluru.assignee()).isEqualTo("E200");

        f.checkIn(OSLO, "E200", "E100"); // Priya flies to Oslo the same day

        assertThat(rankedAtOslo(Set.of())).containsExactly("E100");
    }
}
