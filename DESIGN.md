# Snack Counter: Design

**Goal.** Automate the coordination and the paperwork, not the shopping. A person still buys the snacks; the
system decides *who*, makes sure *only one* person buys, checks the *budget*, files the *HR claim* and reports
the *spend*. The company chat tool is the interface, so there is nothing new to install.

## 1. How a refill moves from "empty" to "reimbursed"

```
REPORTED ─┬─> OFFERED ──commit──> COMMITTED ──receipt──┬─> READY_TO_CLAIM ──> CLAIM_SUBMITTED ─┬─> REIMBURSED
          │    ▲   │  declined / no answer in 2 h /     │   (waits here while HR is down)      └─> CLAIM_REJECTED
          │    └───┘  no receipt in 1 day: next person  └─> AWAITING_ADMIN_APPROVAL (above the limit)
          ├─> NEEDS_VOLUNTEER           nobody available: open call in the channel, admin told
          └─> AWAITING_BUDGET_APPROVAL  budget nearly spent: an admin approves before anyone buys
```

1. **Report.** Anyone posts "the box is empty" in chat. A second report while it is still empty joins the same
   refill, so nobody is asked twice.
2. **Offer.** The fairest volunteer *checked in at that office today* gets a direct message with a **spending
   limit** (what is left this month, at most 1 200 NOK / 3 000 INR) and **2 hours** to commit. If less than a
   small purchase is left (300 NOK / 500 INR), an office admin approves first.
3. **Commit.** The first commit wins and the channel is told "X is refilling, please don't buy". Anyone at that
   office may step in. The buyer has **1 day** to upload the receipt.
4. **Handover.** Declined or a missed deadline: the next fairest person is asked. Nobody left: an open call in
   the channel, and the office admin is told.
5. **Receipt.** Photo, amount, purchase date, optional items. Within the limit, the HR claim is filed **at
   once**; above it, an admin approves first. One rule: *we tell the volunteer a limit; above it, an admin decides.*
6. **HR.** The claim carries what HR accepts: amount, currency, description, receipt image, employee id. Its
   status is checked every minute; approved means REIMBURSED and the volunteer is told. A rejection goes to the
   admin. If HR is down, the claim waits and is retried, never lost.

## 2. Main parts and the data each keeps

Layered **controller → service → repository**. The chat tool and HR sit behind interfaces with simulated
versions. A test fails the build if a layer imports one it shouldn't.

| Part | Responsibility | Data it keeps |
|---|---|---|
| `RefillService` | the lifecycle, one-buyer locking, deadlines, HR filing | none: works on refills |
| `VolunteerSelector` | choosing who buys, fairly | none: derived from refill history and presence |
| `BudgetService` | monthly budget and the spending limit | none: derived from receipts and configuration |
| `MonthlyReportService` | the one report for management | none: derived, using fixed (simulated) exchange rates |
| `RefillRepository` | stores refills | **Refill**: office, status, assignee, deadline, people handed off, receipt (payer, amount, date, items, image), HR claim id, history |
| `VolunteerRepository` | who signed up | employee id, name, home office |
| `PresenceRepository` | who is at which office each day | (employee, date) → office |
| `ChatTool`, `HrSystem` | the outside systems | simulated: the messages and claims sent |
| `RefillScheduler` | every minute: missed deadlines, HR status | none |

**One source of truth.** Only refills (with their receipts) are stored. Budget, fairness and the report are
computed from them, so they can never disagree.

## 3. How the listed problems are prevented

| Problem | Prevention |
|---|---|
| The box sits empty for days | One **named** person is asked, with deadlines. An unfinished refill is handed on automatically. Last resort: an open call in the channel, and the admin is told. |
| Two people buy on the same day | **One open refill per office** and **one commit per refill** (locks plus a state check). The channel is told who is buying; anyone taken off a refill is told "don't buy". |
| A few people do most refills and wait weeks for money | **Fair order**: fewest refills in the last 90 days, then fewest unpaid claims, then longest since their last refill. Claims go to HR as soon as the receipt arrives and are retried. |
| Nobody knows the spend or what gets eaten | A monthly report: each office in its own currency, one total in NOK, top items from receipts, and money still owed to volunteers. |

**Hybrid work, holidays, visitors.** Choice is by **presence at an office today**, so nobody at home or on
holiday is asked, and nobody at home can commit. A visitor refills the office they are in, from that office's
budget and currency; HR pays them in their own payroll currency.
**Time zones.** "Today" and "this month" follow each office's local calendar.
**Mistakes.** Bad requests get a clear error that names the problem; a configuration that can't work stops the
app at startup.

## 4. Trade-offs, and what I would deliberately not build

**Trade-offs**
- **Budget in each office's own currency**; exchange rates only for the report. A rate never decides whether
  snacks can be bought.
- **Admin approval, not a hard stop**, above the limit. The box doesn't stay empty over a 100 NOK overrun.
- **Daily check-in for presence.** Simple, but people forget; the open call is the fallback. Calendar or badge
  data would be next.
- **Clock-time deadlines** (2 h / 1 day, configurable), not working hours. Simpler; presence covers absence.
- **Polling HR** every minute instead of webhooks. It works whatever HR supports.
- **Stepping in is allowed.** Faster, but it can skip the fairness order; the person asked is told at once.
- **In memory, single instance**, for the exercise. In production: a database, a unique constraint for "one open
  refill per office", and optimistic locking instead of in-process locks.

**Deliberately not built**
- **Receipt OCR.** The volunteer types the amount; HR checks the image anyway.
- **Sensors on the box.** Costly hardware for what a chat message solves.
- **Our own app.** Chat is where people already are.
- **Per-snack tracking.** Items on the receipt are enough signal.
- **Real chat and HR integrations, SSO, multiple instances.** Out of scope for the exercise.
- **The bigger fix is policy, not code.** A company card or supplier delivery would remove the reimbursement
  wait entirely. Worth raising with management.

*Assumptions agreed before building:* fairness among volunteers present at the office; 2 h to commit, 1 day to
buy; an admin approves overruns; one report in NOK; visitors may refill where they are. How to run the code,
the tests and the demo: see the [README](README.md).
