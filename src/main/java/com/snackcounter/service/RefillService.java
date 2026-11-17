package com.snackcounter.service;

import static com.snackcounter.model.RefillStatus.AWAITING_ADMIN_APPROVAL;
import static com.snackcounter.model.RefillStatus.AWAITING_BUDGET_APPROVAL;
import static com.snackcounter.model.RefillStatus.CLAIM_SUBMITTED;
import static com.snackcounter.model.RefillStatus.COMMITTED;
import static com.snackcounter.model.RefillStatus.OFFERED;
import static com.snackcounter.model.RefillStatus.READY_TO_CLAIM;

import com.snackcounter.client.ChatTool;
import com.snackcounter.client.ClaimRequest;
import com.snackcounter.client.ClaimStatus;
import com.snackcounter.client.HrSystem;
import com.snackcounter.client.HrUnavailableException;
import com.snackcounter.config.SnackProperties;
import com.snackcounter.config.SnackProperties.OfficeSettings;
import com.snackcounter.exception.NotAllowedException;
import com.snackcounter.exception.RefillConflictException;
import com.snackcounter.model.Money;
import com.snackcounter.model.Office;
import com.snackcounter.model.Receipt;
import com.snackcounter.model.Refill;
import com.snackcounter.model.RefillStatus;
import com.snackcounter.model.Volunteer;
import com.snackcounter.repository.RefillRepository;
import com.snackcounter.repository.VolunteerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The refill lifecycle: empty -> offered -> committed -> receipt -> HR claim -> reimbursed.
 *
 * <p>Concurrency: reporting an empty box locks the office (so there is never more than one open refill
 * per office), and every other step locks the refill (so check-then-act is atomic: two people can never
 * both commit). Lock order is always office then refill, so the two can't deadlock.
 *
 * <p>Logging: one INFO line per business event, WARN when something needs attention (budget, HR down, nobody
 * available). Every line starts with "Refill #id OFFICE:", so {@code grep "Refill #7 "} shows one refill's story.
 * Rejected requests (409/403/400) are logged once, by the exception handler, not here.
 */
@Service
public class RefillService {

    private static final Logger log = LoggerFactory.getLogger(RefillService.class);
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);

    private final RefillRepository refills;
    private final VolunteerSelector selector;
    private final BudgetService budget;
    private final PresenceService presence;
    private final VolunteerRepository volunteers;
    private final ChatTool chat;
    private final HrSystem hr;
    private final SnackProperties properties;
    private final Clock clock;
    private final Map<Office, Object> officeLocks = new EnumMap<>(Office.class);

    public RefillService(RefillRepository refills, VolunteerSelector selector, BudgetService budget, PresenceService presence,
                         VolunteerRepository volunteers, ChatTool chat, HrSystem hr, SnackProperties properties, Clock clock) {
        this.refills = refills;
        this.selector = selector;
        this.budget = budget;
        this.presence = presence;
        this.volunteers = volunteers;
        this.chat = chat;
        this.hr = hr;
        this.properties = properties;
        this.clock = clock;
        for (Office office : Office.values()) {
            officeLocks.put(office, new Object());
        }
    }

    // ------------------------------------------------------------------ 1. the box is empty

    /** Anyone (volunteer or not) reports the box empty. A second report joins the refill already open. */
    public Refill reportEmpty(Office office, String reportedBy) {
        synchronized (officeLocks.get(office)) {
            Optional<Refill> open = refills.openFor(office);
            if (open.isPresent()) {
                log.info("{}: reported empty again by {}, joined the open refill ({})", tag(open.get()), reportedBy,
                        open.get().status());
                chat.direct(reportedBy, "Thanks! The " + office + " box is already being handled (refill #"
                        + open.get().id() + ", " + open.get().status() + ").");
                return open.get();
            }
            Refill refill = refills.create(office, reportedBy, now());
            log.info("{}: reported empty by {}", tag(refill), reportedBy);
            synchronized (refill) {
                if (budget.canAffordTypicalPurchase(office, today(office))) {
                    offerToNext(refill);
                } else {
                    log.warn("{}: budget nearly used up ({} left), waiting for an admin to approve", tag(refill),
                            remaining(office));
                    refill.awaitBudgetApproval(now());
                    tellAdmins(office, "Refill #" + refill.id() + ": only " + remaining(office)
                            + " of this month's budget is left. Approve it to let a volunteer buy anyway.");
                    chat.channel(office, "The snack box is empty, but this month's budget is nearly used up. "
                            + "Waiting for an admin to approve a refill.");
                }
            }
            return refill;
        }
    }

    // ------------------------------------------------------------------ 2. someone commits (or not)

    /**
     * Commit to buying. Allowed for the person who was asked, or for any volunteer stepping in while the
     * refill is still unclaimed. The first commit wins; everyone after gets a conflict.
     */
    public Refill commit(long refillId, String employeeId) {
        Volunteer volunteer = volunteers.require(employeeId);
        Refill refill = refills.require(refillId);
        synchronized (refill) {
            RefillStatus status = refill.status();
            if (status == COMMITTED) {
                throw new RefillConflictException(employeeId.equals(refill.assignee())
                        ? "You have already committed to refill #" + refillId
                        : "Refill #" + refillId + " is already being bought by " + nameOf(refill.assignee()));
            }
            if (status == OFFERED && refill.handedOff().contains(employeeId)) {
                throw new RefillConflictException("Refill #" + refillId + " was handed to someone else; it opens up to "
                        + "everyone again if nobody takes it");
            }
            if (status != OFFERED && status != RefillStatus.NEEDS_VOLUNTEER) {
                throw new RefillConflictException("Refill #" + refillId + " is " + status + " and can't be committed to");
            }
            Office office = refill.office();
            if (!presence.isAt(employeeId, office, today(office))) {
                throw new NotAllowedException(volunteer.name() + " is not checked in at " + office
                        + " today: only people at the office can buy for it");
            }
            String previouslyAsked = refill.assignee();
            refill.commit(employeeId, now().plus(properties.purchaseTimeout()), now());
            boolean steppedIn = previouslyAsked != null && !previouslyAsked.equals(employeeId);
            log.info("{}: committed by {}{}, receipt due {}", tag(refill), employeeId,
                    steppedIn ? " (stepped in for " + previouslyAsked + ")" : "", refill.deadline());

            if (steppedIn) {
                chat.direct(previouslyAsked, volunteer.name() + " picked up refill #" + refillId + ". No need to buy, thanks!");
            }
            chat.direct(employeeId, "Thanks! Spend up to " + budget.spendingCap(office, today(office))
                    + " and upload the receipt by " + local(refill.deadline(), office) + ".");
            chat.channel(office, volunteer.name() + " is refilling the snack box. Please don't buy snacks today.");
            return refill;
        }
    }

    /** The asked or committed volunteer can't do it after all: hand it on straight away. */
    public Refill decline(long refillId, String employeeId) {
        Refill refill = refills.require(refillId);
        synchronized (refill) {
            requireAssignee(refill, employeeId, EnumSet.of(OFFERED, COMMITTED));
            log.info("{}: declined by {}, handing on", tag(refill), employeeId);
            refill.handOff("declined", now());
            offerToNext(refill);
            return refill;
        }
    }

    /** Called on a schedule: whoever missed their deadline is handed off and the next person is asked. */
    public void escalateOverdue() {
        Instant now = now();
        forEachSafely(refills.withStatus(EnumSet.of(OFFERED, COMMITTED)), "checking its deadline", refill -> {
            boolean stillWaiting = refill.status() == OFFERED || refill.status() == COMMITTED;
            if (!stillWaiting || refill.deadline() == null || now.isBefore(refill.deadline())) {
                return;
            }
            String late = refill.assignee();
            boolean hadCommitted = refill.status() == COMMITTED;
            log.info("{}: {} missed the deadline ({}), handing on", tag(refill), late,
                    hadCommitted ? "no receipt" : "no answer");
            refill.handOff(hadCommitted ? "no receipt before the deadline" : "no answer before the deadline", now);
            chat.direct(late, hadCommitted
                    ? "No receipt arrived in time, so refill #" + refill.id() + " was handed to someone else. "
                            + "Please don't buy snacks for it now."
                    : "Refill #" + refill.id() + " was offered to someone else because there was no answer in time.");
            offerToNext(refill);
        });
    }

    // ------------------------------------------------------------------ 3. receipt and money

    /** {@code purchasedOn} may be null: it then defaults to today at the refill's office. */
    public Refill uploadReceipt(long refillId, String employeeId, Money amount, LocalDate purchasedOn,
                                List<String> items, byte[] image) {
        Refill refill = refills.require(refillId);
        synchronized (refill) {
            requireAssignee(refill, employeeId, EnumSet.of(COMMITTED));
            Office office = refill.office();
            OfficeSettings settings = properties.office(office);
            LocalDate boughtOn = purchasedOn != null ? purchasedOn : today(office);
            if (!amount.currency().equals(settings.currency())) {
                throw new IllegalArgumentException("The " + office + " box is paid in " + settings.currency()
                        + ", not " + amount.currency());
            }
            if (!amount.greaterThanZero()) {
                throw new IllegalArgumentException("The receipt amount must be positive");
            }
            if (image == null || image.length == 0) {
                throw new IllegalArgumentException("A receipt image is required for the HR claim");
            }
            if (boughtOn.isAfter(today(office))) {
                throw new IllegalArgumentException("The purchase date can't be in the future");
            }
            LocalDate reportedOn = LocalDate.ofInstant(refill.reportedAt(), office.zone());
            if (boughtOn.isBefore(reportedOn)) {
                throw new IllegalArgumentException("The receipt is dated before the box was reported empty (" + reportedOn + ")");
            }

            Receipt receipt = new Receipt(employeeId, amount, boughtOn, items, image, now());
            Money limit = budget.spendingCap(office, boughtOn); // what the volunteer was told they may spend
            if (!amount.isGreaterThan(limit)) {
                log.info("{}: receipt for {} from {}, within the limit", tag(refill), amount, employeeId);
                refill.recordReceipt(receipt, READY_TO_CLAIM, now());
                submitClaim(refill);
            } else {
                log.warn("{}: receipt for {} from {} is over the spending limit ({}), waiting for an admin",
                        tag(refill), amount, employeeId, limit);
                refill.recordReceipt(receipt, AWAITING_ADMIN_APPROVAL, now());
                tellAdmins(office, "Refill #" + refillId + ": receipt of " + amount + " is over the spending limit ("
                        + limit + "; " + remaining(office) + " of the budget left). Approve it to send the claim to HR.");
                chat.direct(employeeId, "Receipt received. It is over the spending limit, so an admin approves it "
                        + "before it goes to HR. You'll hear from us.");
            }
            chat.channel(office, "The snack box was refilled by " + nameOf(employeeId) + ". Thank you!");
            return refill;
        }
    }

    /** An office admin approves an over-budget refill: before buying, or a receipt that went over. */
    public Refill approveOverBudget(long refillId, String adminId) {
        Refill refill = refills.require(refillId);
        synchronized (refill) {
            if (!properties.office(refill.office()).admins().contains(adminId)) {
                throw new NotAllowedException(adminId + " is not a snack admin for " + refill.office());
            }
            if (refill.status() == AWAITING_BUDGET_APPROVAL) {
                log.info("{}: over-budget refill approved by {}", tag(refill), adminId);
                refill.note("Over-budget refill approved by " + adminId, now());
                offerToNext(refill);
            } else if (refill.status() == AWAITING_ADMIN_APPROVAL) {
                log.info("{}: over-budget receipt approved by {}", tag(refill), adminId);
                refill.readyToClaim(now(), "Over-budget receipt approved by " + adminId);
                submitClaim(refill);
            } else {
                throw new RefillConflictException("Refill #" + refillId + " is " + refill.status() + "; nothing to approve");
            }
            return refill;
        }
    }

    /** Everything the clock drives, in one call: hand off missed deadlines, then retry and follow up HR claims. */
    public void runScheduledChecks() {
        log.debug("Running scheduled checks: missed deadlines, then HR claims");
        escalateOverdue();
        syncClaims();
    }

    /** Called on a schedule: retries claims HR couldn't take, and picks up HR's decisions. */
    public void syncClaims() {
        forEachSafely(refills.withStatus(EnumSet.of(READY_TO_CLAIM, CLAIM_SUBMITTED)), "syncing its HR claim", refill -> {
            if (refill.status() == READY_TO_CLAIM) {
                submitClaim(refill);
            } else if (refill.status() == CLAIM_SUBMITTED) {
                applyHrDecision(refill, hr.status(refill.claimId()));
            }
        });
    }

    public Refill find(long refillId) {
        return refills.require(refillId);
    }

    public List<Refill> all() {
        return refills.all();
    }

    // ------------------------------------------------------------------ helpers (caller holds the refill lock)

    private void offerToNext(Refill refill) {
        Office office = refill.office();
        Optional<Volunteer> next = selector.next(office, today(office), refill.handedOff());
        if (next.isPresent()) {
            Instant until = now().plus(properties.acceptTimeout());
            refill.offer(next.get().employeeId(), until, now());
            log.info("{}: offered to {}, answer by {}", tag(refill), next.get().employeeId(), until);
            chat.direct(next.get().employeeId(), "The " + office + " snack box is empty. Can you refill it today? "
                    + "Reply 'commit " + refill.id() + "' before " + local(until, office) + ". Spend up to "
                    + budget.spendingCap(office, today(office)) + ".");
        } else {
            refill.needVolunteer(now());
            log.warn("{}: no volunteer available at the office, open call in {}", tag(refill), office.channel());
            chat.channel(office, "The snack box is empty and nobody on the rota is available. Anyone at the office "
                    + "can pick it up: 'commit " + refill.id() + "'.");
            tellAdmins(office, "Refill #" + refill.id() + ": nobody on the rota is available at the office today. "
                    + "Can you find someone?");
        }
    }

    private void submitClaim(Refill refill) {
        Receipt receipt = refill.receipt();
        String description = "Snack counter refill #" + refill.id() + ", " + refill.office() + " office, bought "
                + receipt.purchasedOn();
        try {
            String claimId = hr.submitClaim(new ClaimRequest(receipt.paidBy(), receipt.amount(), description, receipt.image()));
            refill.claimSubmitted(claimId, now());
            log.info("{}: HR claim {} filed for {} ({})", tag(refill), claimId, receipt.paidBy(), receipt.amount());
            chat.direct(receipt.paidBy(), "Your claim " + claimId + " for " + receipt.amount()
                    + " is with HR. Approval usually takes 1 to 10 days.");
        } catch (HrUnavailableException e) {
            log.warn("{}: HR unavailable, will retry on the next scheduled check: {}", tag(refill), e.getMessage());
            refill.note("HR unavailable, will retry: " + e.getMessage(), now());
        }
    }

    private void applyHrDecision(Refill refill, ClaimStatus decision) {
        String payer = refill.receipt().paidBy();
        if (decision == ClaimStatus.APPROVED) {
            log.info("{}: reimbursed, HR approved claim {}", tag(refill), refill.claimId());
            refill.reimbursed(now());
            chat.direct(payer, "HR approved claim " + refill.claimId() + ". The money is on its way. Thanks for refilling!");
        } else if (decision == ClaimStatus.REJECTED) {
            log.warn("{}: HR rejected claim {} ({}), admins asked to follow up", tag(refill), refill.claimId(), payer);
            refill.claimRejected(now());
            chat.direct(payer, "HR rejected claim " + refill.claimId() + ". A snack admin will follow up with you.");
            tellAdmins(refill.office(), "HR rejected claim " + refill.claimId() + " for refill #" + refill.id()
                    + " (" + nameOf(payer) + "). Please follow up.");
        }
    }

    private void requireAssignee(Refill refill, String employeeId, EnumSet<RefillStatus> allowed) {
        if (!allowed.contains(refill.status())) {
            throw new RefillConflictException("Refill #" + refill.id() + " is " + refill.status());
        }
        if (!employeeId.equals(refill.assignee())) {
            throw new NotAllowedException("Refill #" + refill.id() + " is assigned to " + nameOf(refill.assignee()));
        }
    }

    private void tellAdmins(Office office, String message) {
        properties.office(office).admins().forEach(admin -> chat.direct(admin, message));
    }

    /** The prefix of every log line about a refill: "Refill #7 OSLO". */
    private static String tag(Refill refill) {
        return "Refill #" + refill.id() + " " + refill.office();
    }

    /** Deadlines as people read them: "18 Nov 10:30 (Oslo time)", not a UTC timestamp. */
    private static String local(Instant instant, Office office) {
        return LOCAL_TIME.format(instant.atZone(office.zone())) + " (" + office.displayName() + " time)";
    }

    /**
     * Scheduled work, one refill at a time: if one fails (HR or the chat tool is down), it is logged and retried
     * on the next run, and the other refills are still processed.
     */
    private void forEachSafely(List<Refill> batch, String what, Consumer<Refill> action) {
        for (Refill refill : batch) {
            try {
                synchronized (refill) {
                    action.accept(refill);
                }
            } catch (HrUnavailableException e) {
                log.warn("{}: HR unavailable while {}, will retry: {}", tag(refill), what, e.getMessage());
            } catch (RuntimeException e) {
                log.error("{}: failed while {}, will retry on the next scheduled check", tag(refill), what, e);
            }
        }
    }

    private Money remaining(Office office) {
        return budget.remaining(office, YearMonth.from(today(office)));
    }

    private String nameOf(String employeeId) {
        return employeeId == null ? "nobody" : volunteers.find(employeeId).map(Volunteer::name).orElse(employeeId);
    }

    private LocalDate today(Office office) {
        return office.today(clock);
    }

    private Instant now() {
        return clock.instant();
    }
}
