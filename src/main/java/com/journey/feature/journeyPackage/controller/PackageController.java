package com.journey.feature.journeyPackage.controller;

import com.journey.feature.journeyPackage.dto.AssignPackageRequest;
import com.journey.feature.journeyPackage.dto.PackageAssignmentDto;
import com.journey.feature.journeyPackage.dto.PackageContextDto;
import com.journey.feature.journeyPackage.dto.PackageDto;
import com.journey.feature.journeyPackage.dto.SavePackageRequest;
import com.journey.feature.journeyPackage.service.PackageAssignmentService;
import com.journey.feature.journeyPackage.service.PackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Access rules live in the services, through AccessPolicy. */
@RestController
@RequiredArgsConstructor
public class PackageController {

    private final PackageService packages;
    private final PackageAssignmentService assignments;

    // ─── Definitions ──────────────────────────────────────────────────────────

    @GetMapping("/api/packages")
    public ResponseEntity<List<PackageDto>> list() {
        return ResponseEntity.ok(packages.list());
    }

    @GetMapping("/api/packages/{id}")
    public ResponseEntity<PackageDto> get(@PathVariable String id) {
        return ResponseEntity.ok(packages.get(id));
    }

    @PostMapping("/api/packages")
    public ResponseEntity<PackageDto> create(@Valid @RequestBody SavePackageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(packages.create(req));
    }

    @PutMapping("/api/packages/{id}")
    public ResponseEntity<PackageDto> update(@PathVariable String id, @Valid @RequestBody SavePackageRequest req) {
        return ResponseEntity.ok(packages.update(id, req));
    }

    @DeleteMapping("/api/packages/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        packages.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Assignments ──────────────────────────────────────────────────────────

    /** GET /api/package-assignments?learnerId= */
    @GetMapping("/api/package-assignments")
    public ResponseEntity<List<PackageAssignmentDto>> listForLearner(@RequestParam String learnerId) {
        return ResponseEntity.ok(assignments.listForLearner(learnerId));
    }

    @GetMapping("/api/package-assignments/{id}")
    public ResponseEntity<PackageAssignmentDto> getAssignment(@PathVariable String id) {
        return ResponseEntity.ok(assignments.get(id));
    }

    @PostMapping("/api/package-assignments")
    public ResponseEntity<PackageAssignmentDto> assign(@Valid @RequestBody AssignPackageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignments.assign(req));
    }

    @PostMapping("/api/package-assignments/{id}/cancel")
    public ResponseEntity<PackageAssignmentDto> cancel(@PathVariable String id) {
        return ResponseEntity.ok(assignments.cancel(id));
    }

    /** GET /api/learner-journeys/:id/packages: the live packages this journey is part of, with "next". */
    @GetMapping("/api/learner-journeys/{id}/packages")
    public ResponseEntity<List<PackageContextDto>> contextFor(@PathVariable String id) {
        return ResponseEntity.ok(assignments.contextFor(id));
    }
}
