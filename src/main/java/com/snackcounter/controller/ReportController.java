package com.snackcounter.controller;

import com.snackcounter.dto.MonthlyReportResponse;
import com.snackcounter.service.MonthlyReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "4. Reports", description = "The one monthly report management asked for")
public class ReportController {

    private final MonthlyReportService reports;

    public ReportController(MonthlyReportService reports) {
        this.reports = reports;
    }

    /** {@code ?month=2026-11}; defaults to the current month. */
    @GetMapping("/monthly")
    @Operation(summary = "Monthly spend: each office in its own currency, plus one total in NOK",
            description = "Also budget left, money still owed to volunteers, and the top items bought.")
    public MonthlyReportResponse monthly(
            @Parameter(description = "yyyy-MM; defaults to the current month", example = "2026-11")
            @RequestParam(required = false) YearMonth month) {
        return month != null ? reports.report(month) : reports.reportForCurrentMonth();
    }
}
