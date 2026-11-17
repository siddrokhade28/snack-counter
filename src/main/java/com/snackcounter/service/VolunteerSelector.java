package com.snackcounter.service;

import com.snackcounter.config.SnackProperties;
import com.snackcounter.model.Office;
import com.snackcounter.model.Refill;
import com.snackcounter.model.Volunteer;
import com.snackcounter.repository.RefillRepository;
import com.snackcounter.repository.VolunteerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Chooses who buys. Only volunteers who are at that office today are asked (hybrid work, holidays and
 * visitors are all handled by presence). Among them the fairest choice comes first:
 *
 * <ol>
 *   <li>fewest refills in the fairness window (default 90 days): spreads the work beyond the usual few</li>
 *   <li>fewest refills still waiting for reimbursement: nobody carries several unpaid receipts at once</li>
 *   <li>longest since their last refill (never refilled = longest)</li>
 *   <li>employee id, so the choice is deterministic and testable</li>
 * </ol>
 * People already handed off for this refill, and people busy with another office's open refill, are skipped.
 */
@Service
public class VolunteerSelector {

    private static final Logger log = LoggerFactory.getLogger(VolunteerSelector.class);

    private final VolunteerRepository volunteers;
    private final PresenceService presence;
    private final RefillRepository refills;
    private final SnackProperties properties;
    private final Clock clock;

    public VolunteerSelector(VolunteerRepository volunteers, PresenceService presence, RefillRepository refills,
                             SnackProperties properties, Clock clock) {
        this.volunteers = volunteers;
        this.presence = presence;
        this.refills = refills;
        this.properties = properties;
        this.clock = clock;
    }

    public Optional<Volunteer> next(Office office, LocalDate today, Set<String> exclude) {
        List<Volunteer> ranking = ranked(office, today, exclude);
        log.debug("Who buys at {} on {}: fairest first {} (excluded {})", office, today,
                ranking.stream().map(Volunteer::employeeId).toList(), exclude);
        return ranking.stream().findFirst();
    }

    public List<Volunteer> ranked(Office office, LocalDate today, Set<String> exclude) {
        List<Refill> history = refills.all();
        Instant windowStart = clock.instant().minus(properties.fairnessWindow());
        Comparator<Volunteer> fairest = Comparator
                .comparingLong((Volunteer v) -> refillsSince(v, history, windowStart))
                .thenComparingLong(v -> refillsAwaitingMoney(v, history))
                .thenComparing(v -> lastRefill(v, history), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Volunteer::employeeId);

        return volunteers.all().stream()
                .filter(v -> presence.isAt(v.employeeId(), office, today))
                .filter(v -> !exclude.contains(v.employeeId()))
                .filter(v -> !busyElsewhere(v, office, history))
                .sorted(fairest)
                .toList();
    }

    private static long refillsSince(Volunteer v, Collection<Refill> history, Instant since) {
        return paidBy(v, history).filter(r -> !r.receipt().uploadedAt().isBefore(since)).count();
    }

    private static long refillsAwaitingMoney(Volunteer v, Collection<Refill> history) {
        return paidBy(v, history).filter(r -> r.status().moneyOwed()).count();
    }

    private static Instant lastRefill(Volunteer v, Collection<Refill> history) {
        return paidBy(v, history).map(r -> r.receipt().uploadedAt()).max(Comparator.naturalOrder()).orElse(null);
    }

    private static boolean busyElsewhere(Volunteer v, Office office, Collection<Refill> history) {
        return history.stream().anyMatch(r -> r.office() != office && r.status().boxEmpty()
                && v.employeeId().equals(r.assignee()));
    }

    private static Stream<Refill> paidBy(Volunteer v, Collection<Refill> history) {
        return history.stream().filter(r -> r.receipt() != null && v.employeeId().equals(r.receipt().paidBy()));
    }
}
