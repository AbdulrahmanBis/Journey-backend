package com.journey.feature.dashboard.controller;

import com.journey.feature.dashboard.dto.LearnerSummaryDto;
import com.journey.feature.dashboard.dto.SeniorSummaryDto;
import com.journey.feature.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * GET /api/dashboard/senior/:seniorId
     * Returns each learner under this senior with all their journey views.
     */
    @GetMapping("/senior/{seniorId}")
    public ResponseEntity<List<LearnerSummaryDto>> getSeniorOverview(
            @PathVariable String seniorId) {
        return ResponseEntity.ok(dashboardService.getSeniorOverview(seniorId));
    }

    /**
     * GET /api/dashboard/manager
     * Returns all seniors → learners → journeys tree.
     */
    @GetMapping("/manager")
    public ResponseEntity<List<SeniorSummaryDto>> getManagerOverview(
            @RequestParam(required = false) String departmentId) {
        return ResponseEntity.ok(dashboardService.getManagerOverview(departmentId));
    }
}
