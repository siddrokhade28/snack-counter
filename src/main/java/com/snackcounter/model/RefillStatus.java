package com.snackcounter.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * The life of one refill, from "the box is empty" to "the volunteer has their money back".
 *
 * <pre>
 * REPORTED ─┬─> OFFERED ──commit──> COMMITTED ──receipt──┬─> READY_TO_CLAIM ──> CLAIM_SUBMITTED ─┬─> REIMBURSED
 *           │     │  ▲  timeout/decline: next person      │        ▲                              └─> CLAIM_REJECTED
 *           │     ▼  │                                   └─> AWAITING_ADMIN_APPROVAL (over budget)
 *           ├─> NEEDS_VOLUNTEER (nobody left; anyone may commit)
 *           └─> AWAITING_BUDGET_APPROVAL (budget nearly spent; admin approves, then OFFERED)
 * </pre>
 */
public enum RefillStatus {
    REPORTED,
    AWAITING_BUDGET_APPROVAL,
    OFFERED,
    NEEDS_VOLUNTEER,
    COMMITTED,
    AWAITING_ADMIN_APPROVAL,
    READY_TO_CLAIM,
    CLAIM_SUBMITTED,
    REIMBURSED,
    CLAIM_REJECTED;

    private static final Set<RefillStatus> BOX_EMPTY =
            EnumSet.of(REPORTED, AWAITING_BUDGET_APPROVAL, OFFERED, NEEDS_VOLUNTEER, COMMITTED);
    private static final Set<RefillStatus> MONEY_OWED =
            EnumSet.of(AWAITING_ADMIN_APPROVAL, READY_TO_CLAIM, CLAIM_SUBMITTED);

    /** The box is still empty: a second "it's empty" report joins this refill instead of starting another. */
    public boolean boxEmpty() {
        return BOX_EMPTY.contains(this);
    }

    /** Snacks were bought and a receipt exists. */
    public boolean hasReceipt() {
        return !boxEmpty();
    }

    /** Company money spent: every receipt except one HR refused to pay. */
    public boolean countsAgainstBudget() {
        return hasReceipt() && this != CLAIM_REJECTED;
    }

    /** The volunteer paid and is still waiting for their money. */
    public boolean moneyOwed() {
        return MONEY_OWED.contains(this);
    }
}
