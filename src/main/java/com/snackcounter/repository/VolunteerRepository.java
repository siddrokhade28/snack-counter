package com.snackcounter.repository;

import com.snackcounter.config.SnackProperties;
import com.snackcounter.model.Volunteer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Who the volunteers are. In production this is the company directory (everyone has a company login)
 * filtered to people who opted in via the chat tool; here it is in memory, seeded from configuration.
 */
@Repository
public class VolunteerRepository {

    private static final Logger log = LoggerFactory.getLogger(VolunteerRepository.class);

    private final Map<String, Volunteer> volunteers = new ConcurrentHashMap<>();

    public VolunteerRepository(SnackProperties properties) {
        properties.volunteers().forEach(seed -> register(new Volunteer(seed.employeeId(), seed.name(), seed.homeOffice())));
        log.info("Loaded {} volunteers per home office: {}", volunteers.size(), volunteers.values().stream()
                .collect(Collectors.groupingBy(Volunteer::homeOffice, TreeMap::new, Collectors.counting())));
    }

    public void register(Volunteer volunteer) {
        volunteers.put(volunteer.employeeId(), volunteer);
    }

    public Optional<Volunteer> find(String employeeId) {
        return Optional.ofNullable(volunteers.get(employeeId));
    }

    public Volunteer require(String employeeId) {
        return find(employeeId).orElseThrow(() -> new IllegalArgumentException("'" + employeeId + "' is not a registered volunteer"));
    }

    public Collection<Volunteer> all() {
        return List.copyOf(volunteers.values());
    }
}
