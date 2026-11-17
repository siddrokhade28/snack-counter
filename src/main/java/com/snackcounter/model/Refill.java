package com.snackcounter.model;

import static com.snackcounter.model.RefillStatus.AWAITING_ADMIN_APPROVAL;
import static com.snackcounter.model.RefillStatus.AWAITING_BUDGET_APPROVAL;
import static com.snackcounter.model.RefillStatus.CLAIM_REJECTED;
import static com.snackcounter.model.RefillStatus.CLAIM_SUBMITTED;
import static com.snackcounter.model.RefillStatus.COMMITTED;
import static com.snackcounter.model.RefillStatus.NEEDS_VOLUNTEER;
import static com.snackcounter.model.RefillStatus.OFFERED;
import static com.snackcounter.model.RefillStatus.READY_TO_CLAIM;
import static com.snackcounter.model.RefillStatus.REIMBURSED;
import static com.snackcounter.model.RefillStatus.REPORTED;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One refill of one office's box. Every state change goes through a guarded transition, so an illegal
 * jump (say, REIMBURSED straight from OFFERED) fails loudly instead of corrupting the data.
 *
 * <p>Thread-safety: mutators are {@code synchronized}; {@link RefillService} also holds the monitor for
 * whole check-then-act steps. Readers use volatile fields, so the volunteer selector can look at
 * other refills without taking their locks (no lock-ordering deadlocks).
 */
public final class Refill {

    public record Event(Instant at, String text) {}

    private final long id;
    private final Office office;
    private final String reportedBy;
    private final Instant reportedAt;
    private final Set<String> handedOff = new LinkedHashSet<>();
    private final List<Event> history = new ArrayList<>();

    private volatile RefillStatus status = REPORTED;
    private volatile String assignee;
    private volatile Instant deadline;
    private volatile Receipt receipt;
    private volatile String claimId;

    public Refill(long id, Office office, String reportedBy, Instant reportedAt) {
        this.id = id;
        this.office = office;
        this.reportedBy = reportedBy;
        this.reportedAt = reportedAt;
        history.add(new Event(reportedAt, "Box reported empty by " + reportedBy));
    }

    // ------------------------------------------------------------------ transitions

    public synchronized void awaitBudgetApproval(Instant at) {
        move(EnumSet.of(REPORTED), AWAITING_BUDGET_APPROVAL, at, "Waiting for an admin: budget nearly used up");
    }

    public synchronized void offer(String employeeId, Instant until, Instant at) {
        move(EnumSet.of(REPORTED, AWAITING_BUDGET_APPROVAL, OFFERED, COMMITTED, NEEDS_VOLUNTEER), OFFERED, at,
                "Offered to " + employeeId + " until " + until);
        assignee = employeeId;
        deadline = until;
    }

    public synchronized void needVolunteer(Instant at) {
        move(EnumSet.of(REPORTED, AWAITING_BUDGET_APPROVAL, OFFERED, COMMITTED), NEEDS_VOLUNTEER, at,
                "Nobody available to ask; open to anyone at the office");
        assignee = null;
        deadline = null;
    }

    public synchronized void commit(String employeeId, Instant until, Instant at) {
        move(EnumSet.of(OFFERED, NEEDS_VOLUNTEER), COMMITTED, at, employeeId + " committed to buy, receipt due " + until);
        assignee = employeeId;
        deadline = until;
    }

    /** The current assignee is taken off this refill and will not be asked again for it. */
    public synchronized void handOff(String reason, Instant at) {
        if (status != OFFERED && status != COMMITTED) {
            throw new IllegalStateException("Refill #" + id + " is " + status + "; nobody to hand off");
        }
        handedOff.add(assignee);
        history.add(new Event(at, assignee + " handed off: " + reason));
        assignee = null;
        deadline = null;
    }

    public synchronized void recordReceipt(Receipt newReceipt, RefillStatus next, Instant at) {
        if (next != READY_TO_CLAIM && next != AWAITING_ADMIN_APPROVAL) {
            throw new IllegalArgumentException("A receipt leads to READY_TO_CLAIM or AWAITING_ADMIN_APPROVAL, not " + next);
        }
        move(EnumSet.of(COMMITTED), next, at, "Receipt for " + newReceipt.amount() + " uploaded by " + newReceipt.paidBy());
        receipt = newReceipt;
        deadline = null;
    }

    public synchronized void readyToClaim(Instant at, String why) {
        move(EnumSet.of(AWAITING_ADMIN_APPROVAL), READY_TO_CLAIM, at, why);
    }

    public synchronized void claimSubmitted(String newClaimId, Instant at) {
        move(EnumSet.of(READY_TO_CLAIM), CLAIM_SUBMITTED, at, "Claim " + newClaimId + " submitted to HR");
        claimId = newClaimId;
    }

    public synchronized void reimbursed(Instant at) {
        move(EnumSet.of(CLAIM_SUBMITTED), REIMBURSED, at, "HR approved claim " + claimId);
    }

    public synchronized void claimRejected(Instant at) {
        move(EnumSet.of(CLAIM_SUBMITTED), CLAIM_REJECTED, at, "HR rejected claim " + claimId);
    }

    public synchronized void note(String text, Instant at) {
        history.add(new Event(at, text));
    }

    private void move(Set<RefillStatus> allowedFrom, RefillStatus to, Instant at, String text) {
        if (!allowedFrom.contains(status)) {
            throw new IllegalStateException("Refill #" + id + " cannot go from " + status + " to " + to);
        }
        status = to;
        history.add(new Event(at, text));
    }

    // ------------------------------------------------------------------ reads

    public long id() {
        return id;
    }

    public Office office() {
        return office;
    }

    public String reportedBy() {
        return reportedBy;
    }

    public Instant reportedAt() {
        return reportedAt;
    }

    public RefillStatus status() {
        return status;
    }

    /** Who is asked (OFFERED) or buying (COMMITTED); the payer once a receipt exists. */
    public String assignee() {
        return assignee;
    }

    public Instant deadline() {
        return deadline;
    }

    public Receipt receipt() {
        return receipt;
    }

    public String claimId() {
        return claimId;
    }

    public synchronized Set<String> handedOff() {
        return Set.copyOf(handedOff);
    }

    public synchronized List<Event> history() {
        return List.copyOf(history);
    }
}
