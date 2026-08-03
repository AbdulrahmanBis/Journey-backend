package com.journey.feature.learnerJourney.controller;

import com.journey.feature.learnerJourney.dto.*;
import com.journey.feature.learnerJourney.service.LearnerJourneyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LearnerJourneyController {

    private final LearnerJourneyService service;

    // ─── Learner-journeys ─────────────────────────────────────────────────────

    /** GET /api/learner-journeys?learnerId= */
    @GetMapping("/api/learner-journeys")
    public ResponseEntity<List<LearnerJourneyViewDto>> getByLearner(
            @RequestParam String learnerId) {
        return ResponseEntity.ok(service.getLearnerJourneyViews(learnerId));
    }

    /** GET /api/learner-journeys/:id — full composed quest-log view */
    @GetMapping("/api/learner-journeys/{id}")
    public ResponseEntity<LearnerJourneyViewDto> getView(@PathVariable String id) {
        return ResponseEntity.ok(service.getLearnerJourneyView(id));
    }

    /** POST /api/learner-journeys — assign a journey to a learner */
    @PostMapping("/api/learner-journeys")
    public ResponseEntity<LearnerJourneyViewDto> assign(
            @Valid @RequestBody AssignJourneyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.assignJourney(req));
    }

    /** PATCH /api/learner-journeys/:id/status */
    @PatchMapping("/api/learner-journeys/{id}/status")
    public ResponseEntity<LearnerJourneyViewDto> updateJourneyStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateJourneyStatusRequest req) {
        return ResponseEntity.ok(service.updateJourneyStatus(id, req));
    }

    // ─── Learner-journey-items ────────────────────────────────────────────────

    /**
     * PATCH /api/learner-journey-items/:id/status
     * Mirrors JS PUT /api/updatejurneyItemStatus
     */
    @PatchMapping("/api/learner-journey-items/{id}/status")
    public ResponseEntity<LearnerJourneyItemDto> updateItemStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateItemStatusRequest req) {
        return ResponseEntity.ok(service.updateItemStatus(id, req));
    }

    /**
     * POST /api/learner-journey-items/:id/notes
     * Mirrors JS POST /api/addNote
     */
    @PostMapping("/api/learner-journey-items/{id}/notes")
    public ResponseEntity<NoteDto> addNote(
            @PathVariable String id,
            @Valid @RequestBody AddNoteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addNote(id, req));
    }
}
