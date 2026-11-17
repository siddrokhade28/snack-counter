package com.snackcounter.controller;

import com.snackcounter.dto.PresenceRequest;
import com.snackcounter.service.PresenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** "I'm at the Oslo office today" / "I'm away". The chat tool's daily check-in would call this. */
@RestController
@RequestMapping("/api/presence")
@Tag(name = "1. Presence", description = "Daily check-in: only volunteers at an office today are asked to buy")
public class PresenceController {

    private final PresenceService presence;

    public PresenceController(PresenceService presence) {
        this.presence = presence;
    }

    @PutMapping("/{employeeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Check in at an office, or mark yourself away",
            description = "Visitors check in where they are (e.g. E202 from Bengaluru at OSLO). office null = away.")
    @ApiResponse(responseCode = "204", description = "Saved")
    @ApiResponse(responseCode = "400", description = "Not a registered volunteer")
    public void setPresence(@PathVariable String employeeId, @RequestBody PresenceRequest request) {
        presence.setPresence(employeeId, request.office(), request.date());
    }
}
