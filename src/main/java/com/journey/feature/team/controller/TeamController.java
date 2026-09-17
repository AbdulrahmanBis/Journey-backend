package com.journey.feature.team.controller;

import com.journey.feature.team.dto.LearnerSnapshotDto;
import com.journey.feature.team.dto.TeamOverviewDto;
import com.journey.feature.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Access rules live in TeamService, through AccessPolicy. */
@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService team;

    /** GET /api/team?departmentId=&seniorId= — KPIs, needs-attention list and the learner table. */
    @GetMapping
    public ResponseEntity<TeamOverviewDto> overview(
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String seniorId) {
        return ResponseEntity.ok(team.overview(departmentId, seniorId));
    }

    /** GET /api/team/learners/:id — one learner's row and attention items, for their profile page. */
    @GetMapping("/learners/{id}")
    public ResponseEntity<LearnerSnapshotDto> learner(@PathVariable String id) {
        return ResponseEntity.ok(team.learner(id));
    }
}
