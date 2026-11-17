package com.snackcounter.dto;

import com.snackcounter.model.Office;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** Daily check-in. {@code office} null means away (home, holiday, sick); {@code date} defaults to today. */
public record PresenceRequest(
        @Schema(description = "Office you are at today; null = away (home, holiday, sick)", example = "OSLO", nullable = true) Office office,
        @Schema(description = "Optional; defaults to today at that office (plan a visit by giving a date)", nullable = true) LocalDate date) {}
