package com.snackcounter.dto;

import com.snackcounter.model.Money;

/** A claim in the simulated HR system, without the receipt image. */
public record HrClaimResponse(String claimId, String employeeId, Money amount, String description, String status) {}
