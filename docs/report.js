// Report behaviour: render the Mermaid diagrams and the filterable "every case handled" table.
// Each case names the real test method that proves it (src/test/java/com/snackcounter/...).

mermaid.initialize({
  startOnLoad: true,
  securityLevel: "strict",
  theme: "base",
  themeVariables: {
    primaryColor: "#e8f0fe", primaryBorderColor: "#0053e2", primaryTextColor: "#0f172a",
    lineColor: "#475569", fontFamily: "ui-sans-serif, system-ui, sans-serif", fontSize: "14px"
  },
  flowchart: { htmlLabels: true, curve: "basis" },
  sequence: { mirrorActors: false }
});

const AREA_STYLE = {
  "Lifecycle": "bg-blue-100 text-blue-900",
  "One buyer": "bg-green-100 text-green-900",
  "Handover": "bg-orange-100 text-orange-900",
  "Who buys": "bg-purple-100 text-purple-900",
  "Budget": "bg-amber-100 text-amber-900",
  "Report": "bg-teal-100 text-teal-900",
  "API": "bg-slate-200 text-slate-900",
  "Architecture": "bg-rose-100 text-rose-900",
  "Logging": "bg-teal-100 text-teal-900",
  "Edge cases": "bg-orange-100 text-orange-900"
};

// [area, case, what happens, test class, test method]
const CASES = [
  ["Lifecycle", "Happy path", "Empty → offered → committed → receipt → HR claim → reimbursed, with messages at every step", "RefillLifecycleTest", "aRefillGoesFromEmptyToReimbursed"],
  ["Lifecycle", "Second report of the same empty box", "Joins the open refill. The reporter is told it is already being handled", "RefillLifecycleTest", "aSecondEmptyReportJoinsTheOpenRefillSoNobodyBuysTwice"],
  ["Lifecycle", "Box empties again after a refill", "A new refill starts", "RefillLifecycleTest", "onceTheBoxIsRefilledTheNextEmptyReportStartsANewRefill"],
  ["Lifecycle", "Someone else uploads the receipt", "403: only the person who committed may upload", "RefillLifecycleTest", "onlyTheVolunteerWhoCommittedCanUploadTheReceipt"],
  ["Lifecycle", "Wrong currency, no image, future date, or 0 amount", "Refused with a clear message. The refill is unchanged", "RefillLifecycleTest", "aReceiptMustBeInTheOfficeCurrencyHaveAnImageAndNotBeFutureDated"],
  ["Lifecycle", "HR system is down", "Claim stays READY_TO_CLAIM and is retried every minute, never lost", "RefillLifecycleTest", "aClaimHrCouldNotTakeIsRetriedInsteadOfLost"],
  ["Lifecycle", "HR rejects the claim", "CLAIM_REJECTED. The volunteer and the office admin are told", "RefillLifecycleTest", "aRejectedClaimIsFlaggedToTheVolunteerAndTheOfficeAdmin"],
  ["Lifecycle", "HR still deciding (1 to 10 days)", "Stays CLAIM_SUBMITTED and is checked again later", "RefillLifecycleTest", "aPendingClaimStaysSubmittedUntilHrDecides"],
  ["Lifecycle", "Steps out of order (commit after reimbursed, approve with nothing to approve)", "409. Unknown refill gives 404", "RefillLifecycleTest", "stepsOutOfOrderAreRejected"],
  ["Lifecycle", "Audit trail", "Every step recorded with a timestamp in the refill history", "RefillLifecycleTest", "theHistoryTellsTheWholeStory"],
  ["One buyer", "A second person commits", "409 naming who is already buying", "CommitConflictTest", "aSecondPersonCannotCommitOnceSomeoneHas"],
  ["One buyer", "The same person commits twice", "Told they have already committed", "CommitConflictTest", "committingTwiceTellsYouYouAlreadyDid"],
  ["One buyer", "Someone steps in before the asked person answers", "Allowed. The asked person is told \"no need to buy\"", "CommitConflictTest", "someoneCanStepInBeforeTheAskedPersonAnswersAndTheAskedPersonIsToldNotToBuy"],
  ["One buyer", "A non-volunteer tries to commit", "Refused: not a registered volunteer", "CommitConflictTest", "onlyRegisteredVolunteersCanCommit"],
  ["One buyer", "8 people commit at the same instant (×20 runs)", "Exactly one wins, the rest get a conflict", "CommitConflictTest", "whenManyCommitAtTheSameTimeExactlyOneWins"],
  ["One buyer", "6 \"empty\" reports at the same instant (×20 runs)", "Only one refill is created", "CommitConflictTest", "simultaneousEmptyReportsCreateOneRefill"],
  ["Handover", "No answer within 2 hours", "Offered to the next fair person. The first person is told", "HandoverTest", "noAnswerWithinTwoHoursOffersItToTheNextPerson"],
  ["Handover", "1 second before the deadline", "Nothing changes", "HandoverTest", "nothingHappensBeforeTheDeadline"],
  ["Handover", "Committed but no receipt within 1 day", "Handed on. The late person is told not to buy, and a late receipt is refused", "HandoverTest", "noReceiptWithinADayHandsItOnAndTellsTheLatePersonNotToBuy"],
  ["Handover", "Handed-off person tries to take it back", "409 while someone else is asked", "HandoverTest", "theHandedOffPersonCannotGrabItBackWhileSomeoneElseIsAsked"],
  ["Handover", "Volunteer declines", "Handed on straight away", "HandoverTest", "decliningHandsItOnStraightAway"],
  ["Handover", "Someone else tries to decline", "403: only the assignee can decline", "HandoverTest", "onlyTheAssigneeCanDecline"],
  ["Handover", "Everyone has passed", "NEEDS_VOLUNTEER: open call in the channel, and anyone (even earlier people) may commit", "HandoverTest", "whenEveryoneHasPassedItIsOpenedUpToAnyoneAtTheOffice"],
  ["Handover", "Scheduler runs twice in a row", "No one is skipped", "HandoverTest", "runningTheEscalationTwiceDoesNotSkipAPerson"],
  ["Who buys", "Working from home, on holiday, or only in yesterday", "Not asked: only people at that office today", "VolunteerSelectorTest", "onlyPeopleCheckedInAtThatOfficeTodayAreAsked"],
  ["Who buys", "Marks themselves away", "Removed for that day", "VolunteerSelectorTest", "markingAwayRemovesYouForThatDay"],
  ["Who buys", "Visitor from the other office", "Asked where they are, not at their home office", "VolunteerSelectorTest", "aVisitorFromTheOtherOfficeCanBeAskedWhereTheyAreNotAtHome"],
  ["Who buys", "Some people refill far more often", "Fewest refills in the last 90 days goes first", "VolunteerSelectorTest", "whoeverDidFewestRecentRefillsIsAskedFirst"],
  ["Who buys", "Refills older than 90 days", "No longer count against you", "VolunteerSelectorTest", "refillsOlderThanTheFairnessWindowAreForgiven"],
  ["Who buys", "Someone is still owed money", "Asked after someone who has been paid", "VolunteerSelectorTest", "someoneStillWaitingForTheirMoneyIsAskedAfterSomeoneWhoWasPaid"],
  ["Who buys", "Tie on fairness", "Whoever refilled longest ago goes first", "VolunteerSelectorTest", "onATieWhoeverRefilledLongestAgoGoesFirst"],
  ["Who buys", "Already handed off for this refill", "Skipped", "VolunteerSelectorTest", "peopleAlreadyHandedOffAreSkipped"],
  ["Who buys", "Busy with the other office's open refill", "Not asked twice", "VolunteerSelectorTest", "someoneBusyWithTheOtherOfficesRefillIsNotAskedTwice"],
  ["Who buys", "Check-in without a date, late evening UTC", "Counts for today at that office, local time (already tomorrow in Bengaluru)", "PresenceServiceTest", "aCheckInWithoutADateCountsForTodayAtThatOfficeLocalTime"],
  ["Who buys", "Goes home after checking in", "\"Away\" clears today's check-in", "PresenceServiceTest", "goingAwayWithoutADateClearsTodaysCheckIn"],
  ["Who buys", "Planned visit next week", "A check-in for a given date is used as given", "PresenceServiceTest", "anExplicitDateIsUsedAsGiven"],
  ["Who buys", "Unknown person checks in", "Refused: not a registered volunteer", "PresenceServiceTest", "onlyRegisteredVolunteersCanCheckIn"],
  ["Budget", "Spend per office", "Tracked in each office's own currency (NOK, INR)", "BudgetTest", "spendingIsTrackedPerOfficeInItsOwnCurrency"],
  ["Budget", "Receipt uploaded in a later month", "Counts in the month it was bought", "BudgetTest", "aReceiptCountsInTheMonthItWasBoughtNotUploaded"],
  ["Budget", "HR rejected a claim", "Not counted as company spend", "BudgetTest", "aRejectedClaimDoesNotCountAgainstTheBudget"],
  ["Budget", "Little budget left", "Volunteer is told to spend no more than what is left (e.g. 800 NOK)", "BudgetTest", "theVolunteerIsToldToSpendNoMoreThanWhatIsLeft"],
  ["Budget", "Budget nearly used up (below 300 NOK)", "Nobody buys until the office admin approves. Another office's admin gets 403", "BudgetTest", "whenTheBudgetIsNearlyUsedUpNobodyBuysUntilAnAdminApproves"],
  ["Budget", "Receipt over the remaining budget", "Waits for admin approval before going to HR", "BudgetTest", "aReceiptOverTheRemainingBudgetWaitsForAnAdminBeforeGoingToHr"],
  ["Budget", "Told \"spend up to 1 200 NOK\", spends 3 000 (budget left)", "Above the limit they were told: an admin approves first", "BudgetTest", "aReceiptAboveTheLimitTheVolunteerWasToldNeedsAnAdminEvenWithBudgetLeft"],
  ["Budget", "22:30 on 30 Nov in Bengaluru", "Still November's budget", "BudgetTest", "bengaluruIsStillInNovemberAt2230LocalTime"],
  ["Budget", "00:30 on 1 Dec in Bengaluru (still 30 Nov in Oslo)", "Bengaluru gets December's budget", "BudgetTest", "bengaluruGetsDecembersBudgetAfterLocalMidnightEvenThoughOsloIsStillInNovember"],
  ["Report", "Totals across both currencies", "Oslo 1 200 NOK + Bengaluru 3 500 INR (= 420 NOK) = 1 620 NOK. Budget 7 800 NOK. Top items", "MonthlyReportTest", "eachOfficeIsInItsOwnCurrencyAndTheTotalIsInNok"],
  ["Report", "Money owed to volunteers", "Shown, and drops once HR pays", "MonthlyReportTest", "moneyStillOwedToVolunteersIsShownAndDropsOnceHrPays"],
  ["Report", "Purchases in other months", "Left out", "MonthlyReportTest", "otherMonthsAreLeftOut"],
  ["Report", "Exchange rate changes during the month", "Each receipt converted at the rate for its purchase date", "MonthlyReportTest", "eachReceiptIsConvertedAtTheRateForItsOwnPurchaseDate"],
  ["Report", "A month with no refills", "Zeroes, not errors", "MonthlyReportTest", "anEmptyMonthReportsZeroesNotErrors"],
  ["Report", "Rates in both directions, unknown currency", "NOK↔INR both work; an unknown currency fails clearly", "MonthlyReportTest", "configuredRatesWorkInBothDirections"],
  ["Report", "Adding NOK to INR by accident", "Refused: conversion must be explicit", "MoneyTest", "nokAndInrCannotBeAddedOrComparedWithoutConverting"],
  ["Report", "Rounding", "2 decimals, banker's rounding", "MoneyTest", "amountsAreKeptToTwoDecimalsWithBankersRounding"],
  ["Report", "Money arithmetic and conversion", "Add, subtract, min/max in one currency. 3 500 INR × 0.12 = 420.00 NOK", "MoneyTest", "arithmeticAndConversion"],
  ["API", "One refill end to end over HTTP", "Presence, empty, step-in commit, 409, receipt, HR approve, report", "RefillApiTest", "aRefillEndToEndOverHttp"],
  ["API", "Rule violations, missing fields, an office other than Oslo or Bengaluru", "Clear HTTP errors that name the problem: 404, 400, 403, 409", "RefillApiTest", "rulesBecomeClearHttpErrors"],
  ["API", "Someone opens Swagger UI for the demo", "Every endpoint is documented, in demo order, with pre-filled examples", "RefillApiTest", "swaggerDocumentsEveryEndpointInDemoOrder"],
  ["API", "Bad id, bad month, wrong HTTP method, unknown URL", "Keep their 400 / 405 / 404 instead of turning into a 500", "RefillApiTest", "springsOwnErrorsKeepTheirStatusInsteadOfBecoming500"],
  ["API", "Unknown currency, invalid base64 image, empty items in the list", "Clear 400s that say what's wrong; empty items are dropped, \"nok\" is accepted", "RefillApiTest", "receiptInputMistakesAreClear400sNot500s"],
  ["Edge cases", "Someone working from home (or at the other office) tries to commit", "Refused (403): only people checked in at that office today", "RefillLifecycleTest", "onlySomeoneCheckedInAtThatOfficeCanCommit"],
  ["Edge cases", "A receipt dated before the box was reported empty", "Refused (400): an old receipt can't be claimed", "RefillLifecycleTest", "aReceiptDatedBeforeTheBoxWasReportedEmptyIsRefused"],
  ["Edge cases", "Receipt items with blanks or nulls", "Dropped, not an error", "RefillLifecycleTest", "emptyItemsOnAReceiptAreDroppedNotAnError"],
  ["Edge cases", "Nobody available at the office", "Open call in the channel, and the office admin is told", "RefillLifecycleTest", "whenNobodyIsAvailableTheAdminsAreToldNotJustTheChannel"],
  ["Edge cases", "HR is down while the scheduler checks claims", "The checks keep going and catch up when HR is back", "RefillLifecycleTest", "hrDownWhileCheckingClaimsDoesNotStopTheChecksAndTheyCatchUpLater"],
  ["Edge cases", "Missing exchange rate for an office's currency", "The app refuses to start, with a clear message", "SnackPropertiesTest", "anOfficeCurrencyWithoutAnExchangeRateIsRejected"],
  ["Edge cases", "An office with no admin", "Refused at startup (over-budget refills would get stuck)", "SnackPropertiesTest", "anOfficeWithoutAnAdminIsRejected"],
  ["Edge cases", "typical-min above typical-max, zero budget", "Refused at startup", "SnackPropertiesTest", "impossibleAmountsAreRejected"],
  ["Edge cases", "Missing office or a zero timeout in the config", "Refused at startup", "SnackPropertiesTest", "aMissingOfficeOrAZeroTimeoutIsRejected"],
  ["Logging", "Following one refill in the log", "Each step is one INFO line starting \"Refill #1 OSLO:\"; receipt images never appear", "RefillLoggingTest", "oneRefillsStoryCanBeFollowedInTheLog"],
  ["Logging", "HR is down, then back", "A WARN that it will retry, then the INFO when the claim is filed", "RefillLoggingTest", "troubleIsAWarningAndTheRetryIsVisible"],
  ["Logging", "Box empty, nobody at the office", "A WARN: open call in the office channel", "RefillLoggingTest", "nobodyAtTheOfficeIsAWarning"],
  ["Architecture", "A layer imports one it shouldn't (e.g. a controller uses a repository)", "The build fails and names the file and import (checked for all 10 layers)", "LayeringTest", "layerOnlyDependsOnWhatItMay"]
];

function renderCases() {
  const body = document.querySelector("#cases-table tbody");
  for (const [area, scenario, outcome, testClass, testMethod] of CASES) {
    const row = document.createElement("tr");
    const cells = [
      `<span class="chip ${AREA_STYLE[area]}">${area}</span>`,
      scenario,
      outcome,
      `<code>${testClass}</code><br><span class="text-xs text-slate-600 break-all">${testMethod}</span>`
    ];
    row.innerHTML = cells.map(cell => `<td>${cell}</td>`).join("");
    row.dataset.search = [area, scenario, outcome, testClass, testMethod].join(" ").toLowerCase();
    body.appendChild(row);
  }
  updateCount(CASES.length);
  document.getElementById("case-total").textContent = CASES.length;
}

function updateCount(visible) {
  document.getElementById("case-count").textContent = `Showing ${visible} of ${CASES.length} cases.`;
}

document.getElementById("case-filter").addEventListener("input", event => {
  const terms = event.target.value.toLowerCase().trim().split(/\s+/).filter(Boolean);
  let visible = 0;
  for (const row of document.querySelectorAll("#cases-table tbody tr")) {
    const match = terms.every(term => row.dataset.search.includes(term));
    row.hidden = !match;
    visible += match ? 1 : 0;
  }
  updateCount(visible);
});

renderCases();
