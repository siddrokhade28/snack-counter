package com.snackcounter.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * What the volunteer bought. {@code items} is optional free text ("chips, bananas") so the monthly
 * report can show what is bought; the image is forwarded to HR as-is.
 */
public record Receipt(String paidBy, Money amount, LocalDate purchasedOn, List<String> items, byte[] image,
                      Instant uploadedAt) {

    public Receipt {
        // optional and free text: drop empty entries instead of failing on them
        items = items == null ? List.of()
                : items.stream().filter(item -> item != null && !item.isBlank()).map(String::strip).toList();
    }
}
