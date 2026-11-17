package com.snackcounter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Who is acting: used for commit ("I'll do it") and decline ("can't, ask someone else"). */
public record EmployeeRequest(
        @Schema(description = "Volunteer's employee id", example = "E100") @NotBlank String employeeId) {}
