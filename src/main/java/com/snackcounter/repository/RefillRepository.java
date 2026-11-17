package com.snackcounter.repository;

import com.snackcounter.exception.RefillNotFoundException;
import com.snackcounter.model.Office;
import com.snackcounter.model.Refill;
import com.snackcounter.model.RefillStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

/** In-memory store of every refill. A real deployment would swap this for a database table. */
@Repository
public class RefillRepository {

    private final Map<Long, Refill> refills = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public Refill create(Office office, String reportedBy, Instant at) {
        Refill refill = new Refill(ids.incrementAndGet(), office, reportedBy, at);
        refills.put(refill.id(), refill);
        return refill;
    }

    public Refill require(long id) {
        Refill refill = refills.get(id);
        if (refill == null) {
            throw new RefillNotFoundException(id);
        }
        return refill;
    }

    public List<Refill> all() {
        return refills.values().stream().sorted(Comparator.comparingLong(Refill::id)).toList();
    }

    /** The refill for this office's currently empty box, if there is one (there is never more than one). */
    public Optional<Refill> openFor(Office office) {
        return all().stream().filter(r -> r.office() == office && r.status().boxEmpty()).findFirst();
    }

    public List<Refill> withStatus(Set<RefillStatus> statuses) {
        return all().stream().filter(r -> statuses.contains(r.status())).toList();
    }
}
