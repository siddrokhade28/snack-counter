package com.snackcounter.service;

import static com.snackcounter.model.Office.BENGALURU;
import static com.snackcounter.model.Office.OSLO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snackcounter.support.SnackFixture;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Check-in rules that used to sit in the controller: default dates, "away", unknown people. */
class PresenceServiceTest {

    private final SnackFixture f = new SnackFixture();

    @Test
    void aCheckInWithoutADateCountsForTodayAtThatOfficeLocalTime() {
        f.clock.set(Instant.parse("2026-11-16T20:00:00Z")); // 21:00 on the 16th in Oslo, 01:30 on the 17th in Bengaluru

        f.presence.setPresence("E200", BENGALURU, null);

        assertThat(f.presence.officeOn("E200", LocalDate.of(2026, 11, 17))).contains(BENGALURU);
        assertThat(f.presence.officeOn("E200", LocalDate.of(2026, 11, 16))).isEmpty();
    }

    @Test
    void goingAwayWithoutADateClearsTodaysCheckIn() {
        f.presence.setPresence("E100", OSLO, null);

        f.presence.setPresence("E100", null, null);

        assertThat(f.presence.officeOn("E100", f.today(OSLO))).isEmpty();
    }

    @Test
    void anExplicitDateIsUsedAsGiven() {
        LocalDate nextMonday = LocalDate.of(2026, 11, 23);

        f.presence.setPresence("E200", OSLO, nextMonday); // Priya plans a visit to Oslo

        assertThat(f.presence.isAt("E200", OSLO, nextMonday)).isTrue();
        assertThat(f.presence.isAt("E200", OSLO, f.today(OSLO))).isFalse();
    }

    @Test
    void onlyRegisteredVolunteersCanCheckIn() {
        assertThatThrownBy(() -> f.presence.setPresence("stranger", OSLO, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a registered volunteer");
    }
}
