package com.journey.feature.certificate.controller;

import com.journey.feature.certificate.dto.CertificateDto;
import com.journey.feature.certificate.service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Access rules live in CertificateService, through AccessPolicy. */
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificates;

    /** GET /api/certificates?learnerId= — the caller's own when learnerId is omitted. */
    @GetMapping
    public ResponseEntity<List<CertificateDto>> list(@RequestParam(required = false) String learnerId) {
        return ResponseEntity.ok(certificates.listForLearner(learnerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CertificateDto> get(@PathVariable String id) {
        return ResponseEntity.ok(certificates.get(id));
    }
}
