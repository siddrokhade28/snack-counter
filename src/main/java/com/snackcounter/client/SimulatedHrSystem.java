package com.snackcounter.client;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the HR system. Claims stay PENDING until someone approves or rejects them (tests, or
 * the simulation endpoints), just like the real 1-10 day approval. Can be switched "down" to prove
 * that claims are retried instead of lost.
 */
@Component
public class SimulatedHrSystem implements HrSystem {

    private static final Logger log = LoggerFactory.getLogger(SimulatedHrSystem.class);

    public record StoredClaim(String id, ClaimRequest request, ClaimStatus status) {}

    private final Map<String, StoredClaim> claims = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();
    private volatile boolean available = true;

    @Override
    public String submitClaim(ClaimRequest request) {
        if (!available) {
            log.warn("[HR] not responding, refused a claim for {} ({})", request.employeeId(), request.amount());
            throw new HrUnavailableException("HR system is not responding");
        }
        String id = "HR-" + ids.incrementAndGet();
        claims.put(id, new StoredClaim(id, request, ClaimStatus.PENDING));
        log.info("[HR] received claim {} for {} ({}), pending", id, request.employeeId(), request.amount());
        return id;
    }

    @Override
    public ClaimStatus status(String claimId) {
        if (!available) {
            throw new HrUnavailableException("HR system is not responding"); // like a real outage: polling fails too
        }
        return require(claimId).status();
    }

    public void approve(String claimId) {
        decide(claimId, ClaimStatus.APPROVED);
    }

    public void reject(String claimId) {
        decide(claimId, ClaimStatus.REJECTED);
    }

    public void setAvailable(boolean available) {
        log.info("[HR] simulated outage {}", available ? "over" : "started");
        this.available = available;
    }

    public List<StoredClaim> claims() {
        return claims.values().stream()
                .sorted(Comparator.comparingInt(claim -> Integer.parseInt(claim.id().substring("HR-".length()))))
                .toList();
    }

    private void decide(String claimId, ClaimStatus decision) {
        StoredClaim claim = require(claimId);
        claims.put(claimId, new StoredClaim(claimId, claim.request(), decision));
        log.info("[HR] claim {} {}", claimId, decision);
    }

    private StoredClaim require(String claimId) {
        StoredClaim claim = claims.get(claimId);
        if (claim == null) {
            throw new NoSuchElementException("No HR claim " + claimId);
        }
        return claim;
    }
}
