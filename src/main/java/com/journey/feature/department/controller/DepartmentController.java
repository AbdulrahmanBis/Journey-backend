package com.journey.feature.department.controller;

import com.journey.feature.department.dto.DepartmentDto;
import com.journey.feature.department.dto.SaveDepartmentRequest;
import com.journey.feature.department.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Access rules live in DepartmentService, through AccessPolicy. */
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departments;

    @GetMapping
    public ResponseEntity<List<DepartmentDto>> list() {
        return ResponseEntity.ok(departments.list());
    }

    @PostMapping
    public ResponseEntity<DepartmentDto> create(@Valid @RequestBody SaveDepartmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departments.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentDto> rename(@PathVariable String id, @Valid @RequestBody SaveDepartmentRequest req) {
        return ResponseEntity.ok(departments.rename(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        departments.delete(id);
        return ResponseEntity.noContent().build();
    }
}
