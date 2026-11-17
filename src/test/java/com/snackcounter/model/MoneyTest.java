package com.snackcounter.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

/** Money never mixes currencies by accident. */
class MoneyTest {

    @Test
    void nokAndInrCannotBeAddedOrComparedWithoutConverting() {
        Money nok = Money.of("100", "NOK");
        Money inr = Money.of("100", "INR");

        assertThatThrownBy(() -> nok.plus(inr)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot combine NOK with INR");
        assertThatThrownBy(() -> nok.isGreaterThan(inr)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void amountsAreKeptToTwoDecimalsWithBankersRounding() {
        assertThat(Money.of("10.005", "NOK").amount()).isEqualByComparingTo("10.00");
        assertThat(Money.of("10.015", "NOK").amount()).isEqualByComparingTo("10.02");
        assertThat(Money.of("450", "NOK")).isEqualTo(Money.of("450.00", "NOK"));
    }

    @Test
    void arithmeticAndConversion() {
        Money a = Money.of("1200", "NOK");
        Money b = Money.of("450.50", "NOK");

        assertThat(a.plus(b)).isEqualTo(Money.of("1650.50", "NOK"));
        assertThat(a.minus(b)).isEqualTo(Money.of("749.50", "NOK"));
        assertThat(a.min(b)).isEqualTo(b);
        assertThat(a.max(b)).isEqualTo(a);
        assertThat(Money.of("3500", "INR").convert(Currency.getInstance("NOK"), new BigDecimal("0.12")))
                .isEqualTo(Money.of("420", "NOK"));
        assertThat(a).hasToString("1200.00 NOK");
    }
}
