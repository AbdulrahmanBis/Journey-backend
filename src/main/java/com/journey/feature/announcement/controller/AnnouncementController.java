package com.journey.feature.announcement.controller;

import com.journey.feature.announcement.dto.AnnouncementDto;
import com.journey.feature.announcement.dto.SaveAnnouncementRequest;
import com.journey.feature.announcement.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Access rules live in AnnouncementService, through AccessPolicy. */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcements;

    /** GET /api/announcements?departmentId= — the announcements page (Manager, HR, Admin). */
    @GetMapping
    public ResponseEntity<List<AnnouncementDto>> list(@RequestParam(required = false) String departmentId) {
        return ResponseEntity.ok(announcements.list(departmentId));
    }

    /** GET /api/announcements/active — the top of the caller's dashboard. */
    @GetMapping("/active")
    public ResponseEntity<List<AnnouncementDto>> active() {
        return ResponseEntity.ok(announcements.active());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnnouncementDto> get(@PathVariable String id) {
        return ResponseEntity.ok(announcements.get(id));
    }

    @PostMapping
    public ResponseEntity<AnnouncementDto> create(@Valid @RequestBody SaveAnnouncementRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(announcements.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AnnouncementDto> update(@PathVariable String id, @Valid @RequestBody SaveAnnouncementRequest req) {
        return ResponseEntity.ok(announcements.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        announcements.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/announcements/{id}/dismiss — remove it from the caller's dashboard. */
    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable String id) {
        announcements.dismiss(id);
        return ResponseEntity.noContent().build();
    }
}
