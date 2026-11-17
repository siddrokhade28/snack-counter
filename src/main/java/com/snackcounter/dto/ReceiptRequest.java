package com.snackcounter.dto;

import com.snackcounter.model.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * A receipt upload. {@code purchasedOn} is optional (defaults to today at that office); the image is
 * base64 because the body is JSON.
 */
public record ReceiptRequest(
        @Schema(description = "The volunteer who committed", example = "E100") @NotBlank String employeeId,
        @Schema(description = "Amount paid", example = "450") @NotNull BigDecimal amount,
        @Schema(description = "Must be the office's currency: NOK for Oslo, INR for Bengaluru", example = "NOK") @NotBlank String currency,
        @Schema(description = "Optional; defaults to today at the office. Never in the future", nullable = true) LocalDate purchasedOn,
        @Schema(description = "Optional; what was bought (feeds the report's top items)", example = "[\"chips\", \"bananas\"]") List<String> items,
        @Schema(description = "Receipt photo, base64; forwarded to HR", example = "AQID") @NotBlank String receiptImageBase64) {

    /** Accepts "nok" as well as "NOK"; an unknown code is a clear 400, not a bare exception. */
    public Money money() {
        try {
            return Money.of(amount, Currency.getInstance(currency.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown currency '" + currency + "': use NOK for Oslo, INR for Bengaluru");
        }
    }

    public byte[] receiptImage() {
        try {
            return Base64.getDecoder().decode(receiptImageBase64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("receiptImageBase64 is not valid base64");
        }
    }
}
