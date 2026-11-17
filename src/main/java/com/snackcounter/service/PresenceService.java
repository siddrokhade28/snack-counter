package com.snackcounter.service;

import com.snackcounter.model.Office;
import com.snackcounter.repository.PresenceRepository;
import com.snackcounter.repository.VolunteerRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Which office (if any) a volunteer is at on a given day. This is what makes hybrid work, holidays and
 * visits to the other office work: a refill is only offered to people who are physically at that
 * counter today, whatever their home office is.
 *
 * <p>Source in production: a one-tap "I'm in the office today" chat check-in (or the calendar / badge
 * system if available). Unknown means "not there": we never send someone at home to the shop.
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private final PresenceRepository presence;
    private final VolunteerRepository volunteers;
    private final Clock clock;

    public PresenceService(PresenceRepository presence, VolunteerRepository volunteers, Clock clock) {
        this.presence = presence;
        this.volunteers = volunteers;
        this.clock = clock;
    }

    /**
     * A volunteer's check-in. {@code office} null means away; {@code date} null means today, in the
     * office they checked in to (or their home office when away).
     */
    public void setPresence(String employeeId, Office office, LocalDate date) {
        Office home = volunteers.require(employeeId).homeOffice();
        LocalDate day = date != null ? date : (office != null ? office : home).today(clock);
        if (office == null) {
            markAway(employeeId, day);
        } else {
            checkIn(employeeId, office, day);
        }
    }

    public void checkIn(String employeeId, Office office, LocalDate date) {
        log.debug("{} checked in at {} for {}", employeeId, office, date);
        presence.save(employeeId, date, office);
    }

    /** Working from home, on holiday or sick: not available for that day. */
    public void markAway(String employeeId, LocalDate date) {
        log.debug("{} is away on {}", employeeId, date);
        presence.delete(employeeId, date);
    }

    public Optional<Office> officeOn(String employeeId, LocalDate date) {
        return presence.find(employeeId, date);
    }

    public boolean isAt(String employeeId, Office office, LocalDate date) {
        return officeOn(employeeId, date).filter(office::equals).isPresent();
    }
}
