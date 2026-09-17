package com.journey.feature.journey.controller;

import com.journey.feature.journey.dto.CreateJourneyRequest;
import com.journey.feature.journey.dto.JourneyDto;
import com.journey.feature.journey.dto.JourneyItemDto;
import com.journey.feature.journey.service.JourneyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/journeys")
@RequiredArgsConstructor
public class JourneyController {

    private final JourneyService journeyService;

    /** GET /api/journeys */
    @GetMapping
    public ResponseEntity<List<JourneyDto>> getAll() {
        return ResponseEntity.ok(journeyService.getAllJourneys());
    }

    /** GET /api/journeys/:id */
    @GetMapping("/{id}")
    public ResponseEntity<JourneyDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(journeyService.getById(id));
    }

    /** GET /api/journeys/:id/units — units with items and quiz answers, for editing (staff). */
    @GetMapping("/{id}/units")
    public ResponseEntity<List<com.journey.feature.journey.dto.JourneyUnitDto>> getUnits(@PathVariable String id) {
        return ResponseEntity.ok(journeyService.getUnitsForJourney(id));
    }

    /** GET /api/journeys/:id/items */
    @GetMapping("/{id}/items")
    public ResponseEntity<List<JourneyItemDto>> getItems(@PathVariable String id) {
        return ResponseEntity.ok(journeyService.getItemsForJourney(id));
    }

    /** POST /api/journeys */
    @PostMapping
    public ResponseEntity<JourneyDto> create(@Valid @RequestBody CreateJourneyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(journeyService.createJourney(req));
    }

    /** PUT /api/journeys/:id */
    @PutMapping("/{id}")
    public ResponseEntity<JourneyDto> update(@PathVariable String id,
                                             @Valid @RequestBody CreateJourneyRequest req) {
        return ResponseEntity.ok(journeyService.updateJourney(id, req));
    }

    /** DELETE /api/journeys/:id */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        journeyService.deleteJourney(id);
        return ResponseEntity.noContent().build();
    }
}
