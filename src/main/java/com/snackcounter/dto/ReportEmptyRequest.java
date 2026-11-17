package com.snackcounter.dto;

import com.snackcounter.model.Office;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** "The box is empty": anyone can report it, volunteer or not. */
public record ReportEmptyRequest(
        @Schema(description = "Which office's box is empty", example = "OSLO") @NotNull Office office,
        @Schema(description = "Employee id of whoever noticed (need not be a volunteer)", example = "E102") @NotBlank String reportedBy) {}
