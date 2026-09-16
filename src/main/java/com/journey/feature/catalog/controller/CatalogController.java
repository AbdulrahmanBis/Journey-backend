package com.journey.feature.catalog.controller;

import com.journey.feature.catalog.dto.CatalogDetailDto;
import com.journey.feature.catalog.dto.CatalogPageDto;
import com.journey.feature.catalog.dto.EnrollRequest;
import com.journey.feature.catalog.dto.EnrollResultDto;
import com.journey.feature.catalog.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The library of journeys and packages. Access rules live in CatalogService. */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalog;

    /**
     * GET /api/catalog?q=&type=&tag=&status=&sort=
     *
     * @param type   CatalogItemType code
     * @param status LearningStatus code (learners only)
     * @param sort   relevance | title | newest | popular
     */
    @GetMapping
    public ResponseEntity<CatalogPageDto> browse(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(catalog.browse(q, type, tag, status, sort));
    }

    @GetMapping("/journeys/{id}")
    public ResponseEntity<CatalogDetailDto> journey(@PathVariable String id) {
        return ResponseEntity.ok(catalog.journeyDetail(id));
    }

    @GetMapping("/packages/{id}")
    public ResponseEntity<CatalogDetailDto> pkg(@PathVariable String id) {
        return ResponseEntity.ok(catalog.packageDetail(id));
    }

    @PostMapping("/enroll")
    public ResponseEntity<EnrollResultDto> enroll(@Valid @RequestBody EnrollRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.enroll(req));
    }
}
