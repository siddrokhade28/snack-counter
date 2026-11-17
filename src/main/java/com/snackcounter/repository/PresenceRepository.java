package com.snackcounter.repository;

import com.snackcounter.model.Office;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** Storage only: which office (if any) each employee is at on each day. Rules live in PresenceService. */
@Repository
public class PresenceRepository {

    private record Key(String employeeId, LocalDate date) {}

    private final Map<Key, Office> presence = new ConcurrentHashMap<>();

    public void save(String employeeId, LocalDate date, Office office) {
        presence.put(new Key(employeeId, date), office);
    }

    public void delete(String employeeId, LocalDate date) {
        presence.remove(new Key(employeeId, date));
    }

    public Optional<Office> find(String employeeId, LocalDate date) {
        return Optional.ofNullable(presence.get(new Key(employeeId, date)));
    }
}
