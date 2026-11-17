# Demo run: every API call, both offices

The whole system over curl, in the order you'd show it: **Oslo (NOK)**, **Bengaluru (INR)**, a **visitor from
India refilling Oslo**, the **budget limits**, **HR approving and rejecting**, and the **monthly report** that
combines both offices in NOK.

- Run it all at once: `./demo.sh` (it runs every `bash` block below, in order).
- Or copy the blocks one by one. Start from a **fresh app**: storage is in memory, so the refill ids (1, 2, 3 …)
  and HR claim ids (HR-1, HR-2 …) below only match a clean start.
- Prefer clicking? Everything here is also in **Swagger UI: http://localhost:8085/swagger-ui.html**
  (example values are pre-filled; tags are numbered in this order), and in the **Postman collection
  `docs/postman/snack-counter.postman_collection.json`** (same story, ids captured automatically, built-in checks).
- Needs `curl` and `jq`. The `jq` filters only shorten the output; drop them to see full responses.

**Demo people** (from `application.yml`)

| Office | Volunteers | Admin | Monthly budget | Typical purchase |
|---|---|---|---|---|
| OSLO | E100 Ingrid, E101 Lars, E102 Sofie | oslo-admin | 6 000 NOK | 300 to 1 200 NOK |
| BENGALURU | E200 Priya, E201 Arjun, E202 Meera | blr-admin | 15 000 INR | 500 to 3 000 INR |

## 0. Start

```sh
mvn spring-boot:run          # JDK 21; the app listens on port 8085
```

```bash
API=${API:-http://localhost:8085}
H='Content-Type: application/json'
```

## A. Oslo (NOK): empty box to reimbursed

### A1. Ingrid and Lars check in at the Oslo office today

Only people at that office today are asked to buy.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X PUT $API/api/presence/E100 -H "$H" -d '{"office":"OSLO"}'
curl -s -o /dev/null -w '%{http_code}\n' -X PUT $API/api/presence/E101 -H "$H" -d '{"office":"OSLO"}'
```

```text
204
204
```

### A2. Sofie reports the Oslo box empty

A refill is created and offered to the fairest volunteer present (a tie, so the lowest employee id: Ingrid).

```bash
curl -s -X POST $API/api/refills/empty -H "$H" -d '{"office":"OSLO","reportedBy":"E102"}' | jq -c '{id, office, status, assignee}'
```

```text
{"id":1,"office":"OSLO","status":"OFFERED","assignee":"E100"}
```

### A3. Lars reports the same empty box

It joins the open refill instead of starting a second one: nobody buys twice.

```bash
curl -s -X POST $API/api/refills/empty -H "$H" -d '{"office":"OSLO","reportedBy":"E101"}' | jq -c '{id, status, assignee}'
```

```text
{"id":1,"status":"OFFERED","assignee":"E100"}
```

### A4. Ingrid commits ("I'll do it")

```bash
curl -s -X POST $API/api/refills/1/commit -H "$H" -d '{"employeeId":"E100"}' | jq -c '{id, status, assignee}'
```

```text
{"id":1,"status":"COMMITTED","assignee":"E100"}
```

### A5. Lars tries to commit too: 409

Only one person can commit. The Oslo channel was already told that Ingrid is buying.

```bash
curl -s -X POST $API/api/refills/1/commit -H "$H" -d '{"employeeId":"E101"}' | jq -c '{status, detail}'
```

```text
{"status":409,"detail":"Refill #1 is already being bought by Ingrid"}
```

### A6. Ingrid uploads her 450 NOK receipt

Within budget, so the HR claim is filed straight away.

```bash
curl -s -X POST $API/api/refills/1/receipt -H "$H" \
  -d '{"employeeId":"E100","amount":450,"currency":"NOK","items":["chips","bananas"],"receiptImageBase64":"AQID"}' \
  | jq -c '{id, status, claimId, receipt: .receipt.amount}'
```

```text
{"id":1,"status":"CLAIM_SUBMITTED","claimId":"HR-1","receipt":{"amount":450.00,"currency":"NOK"}}
```

### A7. HR approves the claim; the next scheduler run marks the refill reimbursed

`/api/sim/tick` runs the every-minute checks now instead of waiting.

```bash
curl -s -X POST $API/api/sim/hr/claims/HR-1/approve
curl -s -X POST $API/api/sim/tick
curl -s $API/api/refills/1 | jq -c '{id, status, claimId}'
```

```text
{"id":1,"status":"REIMBURSED","claimId":"HR-1"}
```

## B. Bengaluru (INR): decline, handover, the right currency

### B1. Priya and Arjun check in at the Bengaluru office

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X PUT $API/api/presence/E200 -H "$H" -d '{"office":"BENGALURU"}'
curl -s -o /dev/null -w '%{http_code}\n' -X PUT $API/api/presence/E201 -H "$H" -d '{"office":"BENGALURU"}'
```

```text
204
204
```

### B2. A colleague who isn't a volunteer reports the Bengaluru box empty

Anyone can report. Priya is asked.

```bash
curl -s -X POST $API/api/refills/empty -H "$H" -d '{"office":"BENGALURU","reportedBy":"E250"}' | jq -c '{id, office, status, assignee}'
```

```text
{"id":2,"office":"BENGALURU","status":"OFFERED","assignee":"E200"}
```

### B3. Priya can't today and declines

The refill moves on to Arjun at once; Priya won't be asked again for this refill.

```bash
curl -s -X POST $API/api/refills/2/decline -H "$H" -d '{"employeeId":"E200"}' | jq -c '{id, status, assignee, handedOff}'
```

```text
{"id":2,"status":"OFFERED","assignee":"E201","handedOff":["E200"]}
```

### B4. Arjun commits

```bash
curl -s -X POST $API/api/refills/2/commit -H "$H" -d '{"employeeId":"E201"}' | jq -c '{id, status, assignee}'
```

```text
{"id":2,"status":"COMMITTED","assignee":"E201"}
```

### B5. A receipt in the wrong currency is refused: 400

The Bengaluru box is paid from the Bengaluru budget, in INR.

```bash
curl -s -X POST $API/api/refills/2/receipt -H "$H" \
  -d '{"employeeId":"E201","amount":300,"currency":"NOK","receiptImageBase64":"AQID"}' | jq -c '{status, detail}'
```

```text
{"status":400,"detail":"The BENGALURU box is paid in INR, not NOK"}
```

### B6. Arjun uploads his 2 500 INR receipt

```bash
curl -s -X POST $API/api/refills/2/receipt -H "$H" \
  -d '{"employeeId":"E201","amount":2500,"currency":"INR","items":["samosas","chai","biscuits"],"receiptImageBase64":"AQID"}' \
  | jq -c '{id, status, claimId, receipt: .receipt.amount}'
```

```text
{"id":2,"status":"CLAIM_SUBMITTED","claimId":"HR-2","receipt":{"amount":2500.00,"currency":"INR"}}
```

## C. A visitor: Meera from Bengaluru refills the Oslo box

### C1. Meera (home office Bengaluru) checks in at Oslo while visiting

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X PUT $API/api/presence/E202 -H "$H" -d '{"office":"OSLO"}'
```

```text
204
```

### C2. The Oslo box is empty again

Fairness: Ingrid already did a refill, so Lars is asked (tied with Meera; the lower id wins the tie).

```bash
curl -s -X POST $API/api/refills/empty -H "$H" -d '{"office":"OSLO","reportedBy":"E100"}' | jq -c '{id, office, status, assignee}'
```

```text
{"id":3,"office":"OSLO","status":"OFFERED","assignee":"E101"}
```

### C3. Meera steps in before Lars answers

Anyone at the office can take an unclaimed refill; Lars is told he doesn't need to buy.

```bash
curl -s -X POST $API/api/refills/3/commit -H "$H" -d '{"employeeId":"E202"}' | jq -c '{id, office, status, assignee}'
```

```text
{"id":3,"office":"OSLO","status":"COMMITTED","assignee":"E202"}
```

### C4. Meera pays 1 100 NOK: Oslo's budget and currency

The claim goes to HR in NOK; HR pays Meera in her payroll currency (INR).

```bash
curl -s -X POST $API/api/refills/3/receipt -H "$H" \
  -d '{"employeeId":"E202","amount":1100,"currency":"NOK","items":["fruit","nuts"],"receiptImageBase64":"AQID"}' \
  | jq -c '{id, status, claimId, receipt: .receipt.amount}'
curl -s $API/api/sim/hr/claims | jq -c '.[] | select(.claimId=="HR-3")'
```

```text
{"id":3,"status":"CLAIM_SUBMITTED","claimId":"HR-3","receipt":{"amount":1100.00,"currency":"NOK"}}
{"claimId":"HR-3","employeeId":"E202","amount":{"amount":1100.00,"currency":"NOK"},"description":"Snack counter refill #3, OSLO office, bought 2026-11-17","status":"PENDING"}
```

## D. Budget limits (Oslo)

Oslo has spent 450 + 1 100 = 1 550 of its 6 000 NOK.

### D1. Three more Oslo refills of 1 200 NOK (refills 4, 5, 6)

Ingrid steps in each time. Oslo has now spent 5 150 NOK, leaving 850.

```bash
for id in 4 5 6; do
  curl -s -o /dev/null -X POST $API/api/refills/empty -H "$H" -d '{"office":"OSLO","reportedBy":"E101"}'
  curl -s -o /dev/null -X POST $API/api/refills/$id/commit -H "$H" -d '{"employeeId":"E100"}'
  curl -s -X POST $API/api/refills/$id/receipt -H "$H" \
    -d '{"employeeId":"E100","amount":1200,"currency":"NOK","items":["fruit"],"receiptImageBase64":"AQID"}' \
    | jq -c '{id, status, claimId}'
done
```

```text
{"id":4,"status":"CLAIM_SUBMITTED","claimId":"HR-4"}
{"id":5,"status":"CLAIM_SUBMITTED","claimId":"HR-5"}
{"id":6,"status":"CLAIM_SUBMITTED","claimId":"HR-6"}
```

### D2. Empty again: Ingrid commits and is told how much she may spend

```bash
curl -s -X POST $API/api/refills/empty -H "$H" -d '{"office":"OSLO","reportedBy":"E102"}' | jq -c '{id, status}'
curl -s -X POST $API/api/refills/7/commit -H "$H" -d '{"employeeId":"E100"}' | jq -c '{id, status, assignee}'
curl -s $API/api/sim/chat | jq -r '[.[] | select(.to=="E100")] | last | .text'
```

```text
{"id":7,"status":"OFFERED"}
{"id":7,"status":"COMMITTED","assignee":"E100"}
Thanks! Spend up to 850.00 NOK and upload the receipt by 18 Nov 07:45 (Oslo time).
```

### D3. She spends 1 000 NOK, more than the 850 left: the claim waits for the Oslo admin

```bash
curl -s -X POST $API/api/refills/7/receipt -H "$H" \
  -d '{"employeeId":"E100","amount":1000,"currency":"NOK","items":["coffee"],"receiptImageBase64":"AQID"}' \
  | jq -c '{id, status, claimId}'
```

```text
{"id":7,"status":"AWAITING_ADMIN_APPROVAL","claimId":null}
```

### D4. Ingrid can't approve her own receipt (403); the Oslo admin can

```bash
curl -s -X POST $API/api/refills/7/approve -H "$H" -d '{"adminId":"E100"}' | jq -c '{status, detail}'
curl -s -X POST $API/api/refills/7/approve -H "$H" -d '{"adminId":"oslo-admin"}' | jq -c '{id, status, claimId}'
```

```text
{"status":403,"detail":"E100 is not a snack admin for OSLO"}
{"id":7,"status":"CLAIM_SUBMITTED","claimId":"HR-7"}
```

### D5. The budget is now used up, so the next refill needs approval before anyone buys

Only the Oslo admin can approve it, not the Bengaluru one.

```bash
curl -s -X POST $API/api/refills/empty -H "$H" -d '{"office":"OSLO","reportedBy":"E101"}' | jq -c '{id, status}'
curl -s -X POST $API/api/refills/8/approve -H "$H" -d '{"adminId":"blr-admin"}' | jq -c '{status, detail}'
curl -s -X POST $API/api/refills/8/approve -H "$H" -d '{"adminId":"oslo-admin"}' | jq -c '{id, status, assignee}'
```

```text
{"id":8,"status":"AWAITING_BUDGET_APPROVAL"}
{"status":403,"detail":"blr-admin is not a snack admin for OSLO"}
{"id":8,"status":"OFFERED","assignee":"E101"}
```

## E. HR rejects a claim (Bengaluru)

### E1. Another Bengaluru refill: Priya commits and pays 800 INR

```bash
curl -s -X POST $API/api/refills/empty -H "$H" -d '{"office":"BENGALURU","reportedBy":"E201"}' | jq -c '{id, office, status, assignee}'
curl -s -o /dev/null -X POST $API/api/refills/9/commit -H "$H" -d '{"employeeId":"E200"}'
curl -s -X POST $API/api/refills/9/receipt -H "$H" \
  -d '{"employeeId":"E200","amount":800,"currency":"INR","items":["juice"],"receiptImageBase64":"AQID"}' \
  | jq -c '{id, status, claimId}'
```

```text
{"id":9,"office":"BENGALURU","status":"OFFERED","assignee":"E200"}
{"id":9,"status":"CLAIM_SUBMITTED","claimId":"HR-8"}
```

### E2. HR rejects Priya's claim and approves Arjun's

A rejected receipt no longer counts against the budget.

```bash
curl -s -X POST $API/api/sim/hr/claims/HR-8/reject
curl -s -X POST $API/api/sim/hr/claims/HR-2/approve
curl -s -X POST $API/api/sim/tick
curl -s $API/api/refills | jq -c '.[] | select(.office=="BENGALURU") | {id, status, claimId}'
```

```text
{"id":2,"status":"REIMBURSED","claimId":"HR-2"}
{"id":9,"status":"CLAIM_REJECTED","claimId":"HR-8"}
```

## F. The monthly report: both offices, each in its own currency, one total in NOK

Simulated rate: 1 INR = 0.12 NOK.

```bash
curl -s $API/api/reports/monthly | jq '{month, offices: [.offices[] | {office, spent, remaining, refills, owedToVolunteers}], totalSpent, totalBudget, owedToVolunteers, topItems: .topItems[0:3]}'
```

```text
{
  "month": "2026-11",
  "offices": [
    {
      "office": "OSLO",
      "spent": {
        "amount": 6150.00,
        "currency": "NOK"
      },
      "remaining": {
        "amount": -150.00,
        "currency": "NOK"
      },
      "refills": 6,
      "owedToVolunteers": {
        "amount": 5700.00,
        "currency": "NOK"
      }
    },
    {
      "office": "BENGALURU",
      "spent": {
        "amount": 2500.00,
        "currency": "INR"
      },
      "remaining": {
        "amount": 12500.00,
        "currency": "INR"
      },
      "refills": 1,
      "owedToVolunteers": {
        "amount": 0.00,
        "currency": "INR"
      }
    }
  ],
  "totalSpent": {
    "amount": 6450.00,
    "currency": "NOK"
  },
  "totalBudget": {
    "amount": 7800.00,
    "currency": "NOK"
  },
  "owedToVolunteers": {
    "amount": 5700.00,
    "currency": "NOK"
  },
  "topItems": [
    {
      "item": "fruit",
      "count": 4
    },
    {
      "item": "bananas",
      "count": 1
    },
    {
      "item": "biscuits",
      "count": 1
    }
  ]
}
```

## G. Look around

### G1. What the Bengaluru channel saw

```bash
curl -s $API/api/sim/chat | jq -r '.[] | select(.to=="#snacks-bengaluru") | .text'
```

```text
Arjun is refilling the snack box. Please don't buy snacks today.
The snack box was refilled by Arjun. Thank you!
Priya is refilling the snack box. Please don't buy snacks today.
The snack box was refilled by Priya. Thank you!
```

### G2. Every claim HR received

```bash
curl -s $API/api/sim/hr/claims | jq -c '.[] | {claimId, employeeId, amount, status}'
```

```text
{"claimId":"HR-1","employeeId":"E100","amount":{"amount":450.00,"currency":"NOK"},"status":"APPROVED"}
{"claimId":"HR-2","employeeId":"E201","amount":{"amount":2500.00,"currency":"INR"},"status":"APPROVED"}
{"claimId":"HR-3","employeeId":"E202","amount":{"amount":1100.00,"currency":"NOK"},"status":"PENDING"}
{"claimId":"HR-4","employeeId":"E100","amount":{"amount":1200.00,"currency":"NOK"},"status":"PENDING"}
{"claimId":"HR-5","employeeId":"E100","amount":{"amount":1200.00,"currency":"NOK"},"status":"PENDING"}
{"claimId":"HR-6","employeeId":"E100","amount":{"amount":1200.00,"currency":"NOK"},"status":"PENDING"}
{"claimId":"HR-7","employeeId":"E100","amount":{"amount":1000.00,"currency":"NOK"},"status":"PENDING"}
{"claimId":"HR-8","employeeId":"E200","amount":{"amount":800.00,"currency":"INR"},"status":"REJECTED"}
```

### G3. The full history of one refill (Bengaluru, refill 2)

```bash
curl -s $API/api/refills/2 | jq -r '.history[].text'
```

```text
Box reported empty by E250
Offered to E200 until 2026-11-17T08:45:15.981880Z
E200 handed off: declined
Offered to E201 until 2026-11-17T08:45:16.002795Z
E201 committed to buy, receipt due 2026-11-18T06:45:16.025286Z
Receipt for 2500.00 INR uploaded by E201
Claim HR-2 submitted to HR
HR approved claim HR-2
```

### G4. Errors are clear: unknown refill (404), missing receipt image (400), an office that doesn't exist (400),
and Sofie, working from home, can't commit to the Oslo refill (403)

```bash
curl -s $API/api/refills/999 | jq -c '{status, detail}'
curl -s -X POST $API/api/refills/8/receipt -H "$H" -d '{"employeeId":"E101","amount":500,"currency":"NOK"}' | jq -c '{status, detail}'
curl -s -X POST $API/api/refills/empty -H "$H" -d '{"office":"PARIS","reportedBy":"E100"}' | jq -c '{status, detail}'
curl -s -X POST $API/api/refills/8/commit -H "$H" -d '{"employeeId":"E102"}' | jq -c '{status, detail}'
```

```text
{"status":404,"detail":"No refill #999"}
{"status":400,"detail":"receiptImageBase64 must not be blank"}
{"status":400,"detail":"Unreadable request body: check the JSON and the values (office must be OSLO or BENGALURU)"}
{"status":403,"detail":"Sofie is not checked in at OSLO today: only people at the office can buy for it"}
```

## H. Optional: watch an automatic handover live

The real deadlines are 2 hours to commit and 1 day to buy, too long for a demo. Restart with a 20-second
deadline instead:

```sh
mvn spring-boot:run -Dspring-boot.run.arguments="--snack.accept-timeout=PT20S --snack.escalation-interval-ms=5000"
```

Then check in Ingrid and Lars (A1), report Oslo empty (A2), do nothing, and after about 25 seconds:

```sh
curl -s $API/api/refills/1 | jq -c '{status, assignee, handedOff}'
# {"status":"OFFERED","assignee":"E101","handedOff":["E100"]}: Ingrid didn't answer, so Lars is asked
```
