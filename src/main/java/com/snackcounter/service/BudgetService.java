package com.snackcounter.service;

import com.snackcounter.config.SnackProperties;
import com.snackcounter.config.SnackProperties.OfficeSettings;
import com.snackcounter.model.Money;
import com.snackcounter.model.Office;
import com.snackcounter.model.Receipt;
import com.snackcounter.model.Refill;
import com.snackcounter.repository.RefillRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The monthly budget check. Always in the office's own currency (6 000 NOK, 15 000 INR), so no exchange
 * rate is ever involved in deciding whether a purchase is allowed. A receipt counts in the month of its
 * purchase date, in the office's local calendar.
 */
@Service
public class BudgetService {

    private final RefillRepository refills;
    private final SnackProperties properties;

    public BudgetService(RefillRepository refills, SnackProperties properties) {
        this.refills = refills;
        this.properties = properties;
    }

    /** The receipts that count against this office's budget for this month. The report uses the same list. */
    public List<Receipt> receipts(Office office, YearMonth month) {
        return refills.all().stream()
                .filter(r -> r.office() == office && r.status().countsAgainstBudget())
                .map(Refill::receipt)
                .filter(receipt -> YearMonth.from(receipt.purchasedOn()).equals(month))
                .toList();
    }

    public Money spent(Office office, YearMonth month) {
        Money zero = Money.zero(properties.office(office).currency());
        return receipts(office, month).stream().map(Receipt::amount).reduce(zero, Money::plus);
    }

    /** Budget minus spent; negative once an admin approved going over. */
    public Money remaining(Office office, YearMonth month) {
        return properties.office(office).budget().minus(spent(office, month));
    }

    /** Is there room for at least a small normal purchase? Otherwise an admin decides before anyone buys. */
    public boolean canAffordTypicalPurchase(Office office, LocalDate today) {
        OfficeSettings settings = properties.office(office);
        return !settings.minPurchase().isGreaterThan(remaining(office, YearMonth.from(today)));
    }

    /**
     * The spending limit: what the volunteer is told they may spend, and what a receipt may cost without an admin's
     * OK. It is what is left this month, but never more than a normal large purchase.
     */
    public Money spendingCap(Office office, LocalDate today) {
        OfficeSettings settings = properties.office(office);
        Money remaining = remaining(office, YearMonth.from(today));
        return remaining.min(settings.maxPurchase()).max(Money.zero(settings.currency()));
    }
}
