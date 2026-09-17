package com.journey.feature.learnerJourney.controller;

import com.journey.feature.learnerJourney.dto.*;
import com.journey.feature.learnerJourney.service.LearnerUnitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** The unit-based journey log. Access rules live in LearnerUnitService. */
@RestController
@RequiredArgsConstructor
public class LearnerUnitController {

    private final LearnerUnitService units;

    @GetMapping("/api/learner-journeys/{id}/outline")
    public ResponseEntity<JourneyOutlineDto> outline(@PathVariable String id) {
        return ResponseEntity.ok(units.outline(id));
    }

    @GetMapping("/api/learner-journey-items/{id}")
    public ResponseEntity<ItemContentDto> item(@PathVariable String id) {
        return ResponseEntity.ok(units.item(id));
    }

    @PostMapping("/api/learner-journey-items/{id}/open")
    public ResponseEntity<OutlineItemDto> open(@PathVariable String id) {
        return ResponseEntity.ok(units.open(id));
    }

    @PostMapping("/api/learner-journey-items/{id}/complete")
    public ResponseEntity<OutlineItemDto> complete(@PathVariable String id) {
        return ResponseEntity.ok(units.complete(id));
    }

    /** { "seconds": 30 } */
    @PostMapping("/api/learner-journey-items/{id}/time")
    public ResponseEntity<Void> time(@PathVariable String id, @RequestBody Map<String, Integer> body) {
        units.addTime(id, body.get("seconds"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/learner-journey-units/{id}/quiz")
    public ResponseEntity<UnitQuizDto> quiz(@PathVariable String id) {
        return ResponseEntity.ok(units.quiz(id));
    }

    @PostMapping("/api/learner-journey-units/{id}/quiz")
    public ResponseEntity<UnitQuizDto> submitQuiz(@PathVariable String id, @Valid @RequestBody SubmitQuizRequest req) {
        return ResponseEntity.ok(units.submitQuiz(id, req));
    }

    @PostMapping("/api/learner-journey-units/{id}/submit")
    public ResponseEntity<LearnerUnitService.OutlineUnitSummary> submit(@PathVariable String id) {
        return ResponseEntity.ok(units.submit(id));
    }

    /** { "status": 1004 } */
    @PatchMapping("/api/learner-journey-units/{id}/status")
    public ResponseEntity<LearnerUnitService.OutlineUnitSummary> status(@PathVariable String id, @RequestBody Map<String, Integer> body) {
        return ResponseEntity.ok(units.updateStatus(id, body.get("status")));
    }

    @PostMapping("/api/learner-journey-units/{id}/notes")
    public ResponseEntity<NoteDto> addNote(@PathVariable String id, @Valid @RequestBody AddNoteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(units.addNote(id, req));
    }
}
