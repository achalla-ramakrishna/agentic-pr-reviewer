package com.codewalnut.prreviewer.controller;

import com.codewalnut.prreviewer.dto.MetricsResponse;
import com.codewalnut.prreviewer.service.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping
    public MetricsResponse get() {
        return metricsService.getMetrics();
    }
}
