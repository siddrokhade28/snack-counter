# Snack Counter Automation

Automates the snack refills at the Oslo and Bengaluru offices: who buys, making sure only one person buys,
the monthly budget check, the HR reimbursement claim, and one monthly spend report.
**Read [DESIGN.md](DESIGN.md) first** (the design document, about 2 pages).
For the visual walkthrough, open **[docs/report.html](docs/report.html)**: it has the flow diagrams, the state machine, and every case handled with its test. It works offline.

Java 21 · Spring Boot 3.5 · in-memory · the chat tool and the HR system are simulated.

## Run

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home   # any JDK 21
mvn test                 # 120 tests, about 5 s
mvn spring-boot:run      # http://localhost:8085
./demo.sh                # in a second terminal: both offices, a visitor, budget limits, HR, the report
```

**Demo it three ways:**
- **Swagger UI: http://localhost:8085/swagger-ui.html**. Work top to bottom; examples are pre-filled
  (OpenAPI spec: `/v3/api-docs`).
- **Postman: import [docs/postman/snack-counter.postman_collection.json](docs/postman/snack-counter.postman_collection.json)**.
  61 requests in demo order. Each one saves the refill/claim ids it gets and checks its own answer (128 checks).
  To run it from the terminal: `npx newman run docs/postman/snack-counter.postman_collection.json`.
- **[docs/DEMO-CURLS.md](docs/DEMO-CURLS.md)**: every API call as curl, with its real output, covering
  Oslo (NOK), Bengaluru (INR), a visitor from India refilling Oslo, budget approval, HR approving and
  rejecting, and the combined report in NOK. `demo.sh` runs exactly these blocks, so the two can't drift apart.

## What the PDF asked for, and where it is

| Deliverable | Where |
|---|---|
| Design document | [`DESIGN.md`](DESIGN.md) |
| Refill lifecycle | `service/RefillService`, `model/Refill`, `model/RefillStatus` |
| Choosing who buys | `service/VolunteerSelector` (+ `service/PresenceService`) |
| Budget check | `service/BudgetService` |
| Simulated chat tool / HR system | `client/SimulatedChatTool`, `client/SimulatedHrSystem` (behind the `ChatTool` / `HrSystem` interfaces) |
| Monthly report across both currencies | `service/MonthlyReportService` |

## Layered architecture (controller → service → repository)

```
com.snackcounter
├── controller   HTTP only: map requests to services, results to DTOs (no rules, no repositories)
├── dto          request and response shapes (ReportEmptyRequest, ReceiptRequest, RefillResponse, ...)
├── service      every business rule: lifecycle, who buys, budget, presence, report
├── repository   storage only, in memory: refills, volunteers, presence
├── model        the domain: Refill (guarded state machine), RefillStatus, Receipt, Money, Office, Volunteer
├── client       outside systems behind interfaces: ChatTool, HrSystem (+ simulated versions)
├── exception    rule violations (not found, conflict, not allowed)
├── handler      ApiExceptionHandler: turns those exceptions into HTTP 404 / 409 / 403 / 400 answers
├── scheduler    every minute: RefillService.runScheduledChecks()
└── config       SnackProperties (application.yml), the Clock
```

A request walks down the layers: `POST /api/refills/7/commit` → `RefillController` → `RefillService.commit`
(locks the refill, checks the rules) → `RefillRepository` / `Refill.commit` → `ChatTool` messages →
`RefillResponse`. **`LayeringTest` enforces the boundaries on every build**: for example, a controller that
imports a repository fails the build.

## Tests that prove the rules

| Rule | Test class |
|---|---|
| Two people cannot both commit to the same refill, including an 8-thread race repeated 20 times | `CommitConflictTest` |
| A refill nobody completes is handed to someone else | `HandoverTest` |
| Budgets and totals are correct across both currencies | `BudgetTest`, `MonthlyReportTest`, `MoneyTest` |
| Choosing who buys is fair and respects presence and visitors | `VolunteerSelectorTest`, `PresenceServiceTest` |
| Empty → reimbursed, duplicate reports, HR down or rejecting | `RefillLifecycleTest` |
| The whole app over HTTP, plus error codes | `RefillApiTest` |
| Controller → service → repository boundaries hold | `LayeringTest` |
| The log tells each refill's story, flags trouble, never holds receipt images | `RefillLoggingTest` |

Domain tests wire the real classes by hand (`support/SnackFixture`) with a test-controlled clock
(`support/MutableClock`). Timeouts are tested by moving time forward, not by sleeping.

## Logging

| Level | What you see | Where it comes from |
|---|---|---|
| INFO | Each business event: reported empty, offered to, committed, declined, receipt, HR claim filed, reimbursed | `RefillService` |
| INFO | Every chat message (`[chat -> E100] ...`) and HR event (`[HR] claim HR-1 APPROVED`) | the simulated clients |
| WARN | Needs attention: over budget, nobody at the office, HR down (retrying), HR rejected, and rejected requests (`409 Conflict on POST /api/refills/1/commit: ...`) | `RefillService`, `ApiExceptionHandler` |
| ERROR | A real bug: a 500, logged with its stack trace | `ApiExceptionHandler` |
| DEBUG | The fairness ranking (`fairest first [E100, E101]`), check-ins, scheduler runs | `VolunteerSelector`, `PresenceService` |

- **Follow one refill:** every line starts with `Refill #id OFFICE:`, so `grep "Refill #2 "` shows its whole story.
- **More detail:** `mvn spring-boot:run -Dspring-boot.run.arguments="--logging.level.com.snackcounter=DEBUG"`.
- **Never logged:** receipt images (checked by `RefillLoggingTest`). Each rejected request is logged once, by the
  handler, not again in the service.

## API (what the chat tool would call)

| Method and path | Purpose |
|---|---|
| `PUT /api/presence/{employeeId}` `{"office":"OSLO"}` (or `null` = away) | daily check-in |
| `POST /api/refills/empty` `{"office","reportedBy"}` | report the box empty |
| `POST /api/refills/{id}/commit` `{"employeeId"}` | "I'll do it" (409 if someone already has) |
| `POST /api/refills/{id}/decline` `{"employeeId"}` | "Can't, ask someone else" |
| `POST /api/refills/{id}/receipt` `{"employeeId","amount","currency","purchasedOn?","items?","receiptImageBase64"}` | upload the receipt |
| `POST /api/refills/{id}/approve` `{"adminId"}` | admin approves going over budget |
| `GET /api/refills`, `GET /api/refills/{id}` | status and history |
| `GET /api/reports/monthly?month=2026-11` | the management report |
| `GET /api/sim/chat`, `GET /api/sim/hr/claims`, `POST /api/sim/hr/claims/{id}/approve or reject`, `POST /api/sim/tick` | demo controls for the simulated systems |

All rules (budgets, typical purchase range, admins, timeouts, fairness window, exchange rates, demo
volunteers) are in `src/main/resources/application.yml`.
