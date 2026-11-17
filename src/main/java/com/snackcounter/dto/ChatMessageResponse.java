package com.snackcounter.dto;

import java.time.Instant;

/** One message the simulated chat tool sent: {@code to} is an employee id or a channel such as #snacks-oslo. */
public record ChatMessageResponse(Instant at, String to, String text) {}
