package com.snackcounter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** An office admin approving an over-budget refill. */
public record AdminRequest(
        @Schema(description = "Admin of the refill's office: oslo-admin or blr-admin", example = "oslo-admin")
        @NotBlank String adminId) {}
