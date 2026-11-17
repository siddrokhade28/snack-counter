package com.snackcounter.dto;

import com.snackcounter.model.Money;
import com.snackcounter.model.Office;
import java.time.YearMonth;
import java.util.List;

/**
 * The one monthly report management asked for. Each office in its own currency (what its budget is in),
 * plus one combined total in the report currency.
 *
 * @param owedToVolunteers money volunteers paid and are still waiting for, as of now
 */
public record MonthlyReportResponse(YearMonth month, String reportCurrency, List<OfficeLine> offices,
                            Money totalSpent, Money totalBudget, int totalRefills, Money owedToVolunteers,
                            List<ItemCount> topItems) {

    public record OfficeLine(Office office, Money budget, Money spent, Money remaining, int refills,
                             Money spentInReportCurrency, Money owedToVolunteers) {}

    public record ItemCount(String item, long count) {}
}
