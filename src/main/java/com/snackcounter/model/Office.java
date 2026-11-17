package com.snackcounter.model;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** The two offices. Each has its own time zone, so "today" and "this month" are always the office's local ones. */
public enum Office {
    OSLO(ZoneId.of("Europe/Oslo")),
    BENGALURU(ZoneId.of("Asia/Kolkata"));

    private final ZoneId zone;

    Office(ZoneId zone) {
        this.zone = zone;
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(zone));
    }

    /** Name of the office's snack channel in the chat tool. */
    public String channel() {
        return "#snacks-" + name().toLowerCase();
    }

    /** "Oslo", "Bengaluru": for messages people read. */
    public String displayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}

