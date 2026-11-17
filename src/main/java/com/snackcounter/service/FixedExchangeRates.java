package com.snackcounter.service;

import com.snackcounter.config.SnackProperties;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Simulated rates from configuration, the same on every date. In production this would read the finance
 * team's daily rate (or the rate HR used); the interface already takes the date for that reason.
 */
@Service
public class FixedExchangeRates implements ExchangeRates {

    private final Map<Currency, BigDecimal> toReportCurrency;

    public FixedExchangeRates(SnackProperties properties) {
        this.toReportCurrency = Map.copyOf(properties.report().rates());
    }

    @Override
    public BigDecimal rate(Currency from, Currency to, LocalDate on) {
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }
        return known(from).divide(known(to), MathContext.DECIMAL64);
    }

    private BigDecimal known(Currency currency) {
        BigDecimal rate = toReportCurrency.get(currency);
        if (rate == null) {
            throw new IllegalStateException("No exchange rate configured for " + currency);
        }
        return rate;
    }
}
