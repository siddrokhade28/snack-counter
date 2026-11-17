package com.snackcounter.controller;

import com.snackcounter.dto.AdminRequest;
import com.snackcounter.dto.EmployeeRequest;
import com.snackcounter.dto.ReceiptRequest;
import com.snackcounter.dto.RefillResponse;
import com.snackcounter.dto.ReportEmptyRequest;
import com.snackcounter.service.RefillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The refill lifecycle over HTTP: maps requests to {@link RefillService} and refills to responses, nothing
 * else. In production the chat tool's commands and buttons would call these endpoints.
 */
@RestController
@RequestMapping("/api/refills")
@Tag(name = "2. Refills", description = "The lifecycle: empty, offered, committed, receipt, HR claim, reimbursed")
public class RefillController {

    private final RefillService refills;

    public RefillController(RefillService refills) {
        this.refills = refills;
    }

    @PostMapping("/empty")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report a box empty",
            description = "Creates a refill and asks the fairest volunteer who is at that office today. If the box is "
                    + "already being handled, returns that refill instead (nobody buys twice). If the budget is nearly "
                    + "used up, waits for the office admin (AWAITING_BUDGET_APPROVAL).")
    public RefillResponse reportEmpty(@Valid @RequestBody ReportEmptyRequest request) {
        return RefillResponse.of(refills.reportEmpty(request.office(), request.reportedBy()));
    }

    @PostMapping("/{id}/commit")
    @Operation(summary = "Commit to buying (\"I'll do it\")",
            description = "The asked volunteer, or anyone stepping in while it's unclaimed. The first commit wins.")
    @ApiResponse(responseCode = "200", description = "Committed: the office channel is told not to buy")
    @ApiResponse(responseCode = "409", description = "Someone already committed, or the refill has moved on")
    public RefillResponse commit(@PathVariable long id, @Valid @RequestBody EmployeeRequest request) {
        return RefillResponse.of(refills.commit(id, request.employeeId()));
    }

    @PostMapping("/{id}/decline")
    @Operation(summary = "Decline (\"can't, ask someone else\")",
            description = "Hands the refill to the next fairest volunteer straight away.")
    @ApiResponse(responseCode = "403", description = "Only the person asked (or committed) can decline")
    public RefillResponse decline(@PathVariable long id, @Valid @RequestBody EmployeeRequest request) {
        return RefillResponse.of(refills.decline(id, request.employeeId()));
    }

    @PostMapping("/{id}/receipt")
    @Operation(summary = "Upload the receipt",
            description = "Within budget: the HR claim is filed immediately (CLAIM_SUBMITTED). Over the remaining "
                    + "budget: waits for the office admin (AWAITING_ADMIN_APPROVAL).")
    @ApiResponse(responseCode = "400", description = "Wrong currency for the office, no image, future date or 0 amount")
    @ApiResponse(responseCode = "403", description = "Only the volunteer who committed can upload")
    public RefillResponse uploadReceipt(@PathVariable long id, @Valid @RequestBody ReceiptRequest request) {
        return RefillResponse.of(refills.uploadReceipt(id, request.employeeId(), request.money(),
                request.purchasedOn(), request.items(), request.receiptImage()));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Admin approves going over budget",
            description = "Before buying (AWAITING_BUDGET_APPROVAL becomes OFFERED) or for a receipt over budget "
                    + "(AWAITING_ADMIN_APPROVAL: the HR claim is filed).")
    @ApiResponse(responseCode = "403", description = "Not an admin of this refill's office")
    public RefillResponse approveOverBudget(@PathVariable long id, @Valid @RequestBody AdminRequest request) {
        return RefillResponse.of(refills.approveOverBudget(id, request.adminId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One refill, with its full history")
    @ApiResponse(responseCode = "404", description = "No refill with that id")
    public RefillResponse get(@PathVariable long id) {
        return RefillResponse.of(refills.find(id));
    }

    @GetMapping
    @Operation(summary = "All refills, both offices")
    public List<RefillResponse> list() {
        return refills.all().stream().map(RefillResponse::of).toList();
    }
}
