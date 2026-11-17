package com.snackcounter.service;

import com.snackcounter.config.SnackProperties;
import com.snackcounter.dto.MonthlyReportResponse;
import com.snackcounter.dto.MonthlyReportResponse.ItemCount;
import com.snackcounter.dto.MonthlyReportResponse.OfficeLine;
import com.snackcounter.model.Money;
import com.snackcounter.model.Office;
import com.snackcounter.model.Receipt;
import com.snackcounter.model.Refill;
import com.snackcounter.repository.RefillRepository;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Builds the monthly report. Spending per office comes from {@link BudgetService} (the same numbers the
 * budget check uses). The combined total converts each receipt at the rate for its purchase date, so it
 * stays right once real daily rates replace the simulated ones.
 */
@Service
public class MonthlyReportService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportService.class);

    private static final int TOP_ITEMS = 5;

    private final RefillRepository refills;
    private final BudgetService budget;
    private final ExchangeRates rates;
    private final SnackProperties properties;
    private final Clock clock;

    public MonthlyReportService(RefillRepository refills, BudgetService budget, ExchangeRates rates,
                                SnackProperties properties, Clock clock) {
        this.refills = refills;
        this.budget = budget;
        this.rates = rates;
        this.properties = properties;
        this.clock = clock;
    }

    /** The current month by Oslo's calendar, since the combined total is reported in NOK. */
    public MonthlyReportResponse reportForCurrentMonth() {
        return report(YearMonth.now(clock.withZone(Office.OSLO.zone())));
    }

    public MonthlyReportResponse report(YearMonth month) {
        Currency reportCurrency = properties.report().currency();
        Money zero = Money.zero(reportCurrency);
        List<Refill> all = refills.all();

        List<OfficeLine> lines = new ArrayList<>();
        for (Office office : Office.values()) {
            List<Receipt> receipts = budget.receipts(office, month);
            Money spentConverted = receipts.stream().map(this::toReportCurrency).reduce(zero, Money::plus);
            Money owed = all.stream()
                    .filter(r -> r.office() == office && r.status().moneyOwed())
                    .map(r -> r.receipt().amount())
                    .reduce(Money.zero(properties.office(office).currency()), Money::plus);
            lines.add(new OfficeLine(office, properties.office(office).budget(), budget.spent(office, month),
                    budget.remaining(office, month), receipts.size(), spentConverted, owed));
        }

        Money totalSpent = lines.stream().map(OfficeLine::spentInReportCurrency).reduce(zero, Money::plus);
        Money totalBudget = lines.stream().map(l -> convert(l.budget(), month)).reduce(zero, Money::plus);
        Money totalOwed = lines.stream().map(l -> convert(l.owedToVolunteers(), month)).reduce(zero, Money::plus);
        int totalRefills = lines.stream().mapToInt(OfficeLine::refills).sum();
        MonthlyReportResponse report = new MonthlyReportResponse(month, reportCurrency.getCurrencyCode(), List.copyOf(lines),
                totalSpent, totalBudget, totalRefills, totalOwed, topItems(month));
        log.info("Monthly report {}: {} refills, {} spent of {}, {} still owed to volunteers", month, totalRefills,
                totalSpent, totalBudget, totalOwed);
        return report;
    }

    private Money toReportCurrency(Receipt receipt) {
        Currency target = properties.report().currency();
        Money amount = receipt.amount();
        return amount.convert(target, rates.rate(amount.currency(), target, receipt.purchasedOn()));
    }

    /** Budgets and open debts have no purchase date; they use the rate on the first day of the month. */
    private Money convert(Money amount, YearMonth month) {
        Currency target = properties.report().currency();
        return amount.convert(target, rates.rate(amount.currency(), target, month.atDay(1)));
    }

    private List<ItemCount> topItems(YearMonth month) {
        Map<String, Long> counts = Arrays.stream(Office.values())
                .flatMap(office -> budget.receipts(office, month).stream())
                .flatMap(r -> r.items().stream())
                .map(item -> item.strip().toLowerCase(Locale.ROOT))
                .filter(item -> !item.isEmpty())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_ITEMS)
                .map(e -> new ItemCount(e.getKey(), e.getValue()))
                .toList();
    }
}
