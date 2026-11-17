package com.snackcounter.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

/** Exchange rates for reporting only. Budget decisions never use them (they stay in each office's currency). */
public interface ExchangeRates {

    /** 1 unit of {@code from} = the returned number of units of {@code to}, on that date. */
    BigDecimal rate(Currency from, Currency to, LocalDate on);
}
