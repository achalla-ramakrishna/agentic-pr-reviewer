package com.skyroute.report;

import com.skyroute.common.error.BusinessRuleException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN only (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/route-revenue")
    public List<ReportService.RouteRevenue> routeRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("INVALID_RANGE", "'to' must not be before 'from'");
        }
        return reports.routeRevenue(from, to);
    }

    @GetMapping("/load-factor")
    public List<ReportService.FlightLoad> loadFactor(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reports.loadFactor(date);
    }
}
