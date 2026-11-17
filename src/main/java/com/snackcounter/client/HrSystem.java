package com.snackcounter.client;

/**
 * The external HR system (run by another company). It accepts reimbursement claims and approves or
 * rejects them in 1 to 10 days; it pays the employee in their payroll currency.
 */
public interface HrSystem {

    /**
     * @return HR's id for the new claim
     * @throws HrUnavailableException when HR can't take the claim right now (it will be retried)
     */
    String submitClaim(ClaimRequest request);

    ClaimStatus status(String claimId);
}
