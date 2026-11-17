package com.snackcounter.client;

import com.snackcounter.model.Money;
import java.util.Objects;

/**
 * Exactly what HR needs to create a claim: amount, currency (inside {@code amount}), description,
 * receipt image and employee id. The amount is in the currency of the receipt; HR pays out in the
 * employee's payroll currency, which matters for visitors.
 */
public record ClaimRequest(String employeeId, Money amount, String description, byte[] receiptImage) {

    public ClaimRequest {
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(description, "description");
        if (receiptImage == null || receiptImage.length == 0) {
            throw new IllegalArgumentException("HR requires a receipt image");
        }
    }
}
