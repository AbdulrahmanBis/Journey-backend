package com.journey.feature.metrics.controller;

import com.journey.feature.metrics.dto.GroupMetricsDto;
import com.journey.feature.metrics.dto.LearnerMetricsDto;
import com.journey.feature.metrics.dto.OrgMetricsDto;
import com.journey.feature.metrics.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    /**
     * GET /api/metrics/learner/:id
     * Individual learner view — hours, completion %, exam stats.
     */
    @GetMapping("/learner/{id}")
    public ResponseEntity<LearnerMetricsDto> getLearnerMetrics(@PathVariable String id) {
        return ResponseEntity.ok(metricsService.getLearnerMetrics(id));
    }

    /**
     * GET /api/metrics/senior/:id
     * Aggregated team view for one senior — mirrors JS /api/metrics when role=senior.
     */
    @GetMapping("/senior/{id}")
    public ResponseEntity<GroupMetricsDto> getSeniorMetrics(@PathVariable String id) {
        return ResponseEntity.ok(metricsService.getSeniorMetrics(id));
    }

    /**
     * GET /api/metrics/org
     * Full org view — all seniors → all learners — mirrors JS /api/metrics when role=manager|admin.
     */
    @GetMapping("/org")
    public ResponseEntity<OrgMetricsDto> getOrgMetrics(@RequestParam(required = false) String departmentId) {
        return ResponseEntity.ok(metricsService.getOrgMetrics(departmentId));
    }
}
