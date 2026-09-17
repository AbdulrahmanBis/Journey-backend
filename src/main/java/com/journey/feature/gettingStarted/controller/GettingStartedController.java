package com.journey.feature.gettingStarted.controller;

import com.journey.feature.gettingStarted.dto.GettingStartedDto;
import com.journey.feature.gettingStarted.service.GettingStartedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Always about the caller; there is no way to read or hide someone else's checklist. */
@RestController
@RequestMapping("/api/getting-started")
@RequiredArgsConstructor
public class GettingStartedController {

    private final GettingStartedService gettingStarted;

    @GetMapping
    public ResponseEntity<GettingStartedDto> get() {
        return ResponseEntity.ok(gettingStarted.forCaller());
    }

    @PostMapping("/dismiss")
    public ResponseEntity<Void> dismiss() {
        gettingStarted.dismiss();
        return ResponseEntity.noContent().build();
    }
}
