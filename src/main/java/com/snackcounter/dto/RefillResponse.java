package com.snackcounter.dto;

import com.snackcounter.model.Money;
import com.snackcounter.model.Office;
import com.snackcounter.model.Receipt;
import com.snackcounter.model.Refill;
import com.snackcounter.model.RefillStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** JSON shape of a refill. Leaves out the receipt image (it goes to HR, not to API clients). */
public record RefillResponse(long id, Office office, RefillStatus status, String reportedBy, Instant reportedAt,
                         String assignee, Instant deadline, ReceiptView receipt, String claimId,
                         Set<String> handedOff, List<Refill.Event> history) {

    public record ReceiptView(String paidBy, Money amount, LocalDate purchasedOn, List<String> items) {}

    public static RefillResponse of(Refill refill) {
        Receipt receipt = refill.receipt();
        ReceiptView receiptView = receipt == null ? null
                : new ReceiptView(receipt.paidBy(), receipt.amount(), receipt.purchasedOn(), receipt.items());
        return new RefillResponse(refill.id(), refill.office(), refill.status(), refill.reportedBy(), refill.reportedAt(),
                refill.assignee(), refill.deadline(), receiptView, refill.claimId(), refill.handedOff(), refill.history());
    }
}
