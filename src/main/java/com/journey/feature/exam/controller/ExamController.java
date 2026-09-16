package com.journey.feature.exam.controller;

import com.journey.feature.exam.dto.GradeAttemptRequest;
import com.journey.feature.exam.dto.SaveExamRequest;
import com.journey.feature.exam.dto.SubmitAttemptRequest;
import com.journey.feature.exam.dto.ExamAttemptDto;
import com.journey.feature.exam.dto.ExamDto;
import com.journey.feature.exam.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ExamController {

    private final ExamService service;

    // ─── Exams ───────────────────────────────────────────────────────────────

    /** GET /api/journeys/:journeyId/exam */
    @GetMapping("/api/journeys/{journeyId}/exam")
    public ResponseEntity<ExamDto> getExam(
            @PathVariable String journeyId) {
        return ResponseEntity.ok(service.getExam(journeyId));
    }

    /** POST /api/journeys/:journeyId/exam */
    @PostMapping("/api/journeys/{journeyId}/exam")
    public ResponseEntity<ExamDto> saveExam(
            @PathVariable String journeyId,
            @Valid @RequestBody SaveExamRequest req) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.saveExam(journeyId, req));
    }

    /** DELETE /api/journeys/:journeyId/exam */
    @DeleteMapping("/api/journeys/{journeyId}/exam")
    public ResponseEntity<Void> deleteExam(
            @PathVariable String journeyId) {
        service.deleteExam(journeyId);
        return ResponseEntity.noContent().build();
    }

    // ─── Exam Attempts ───────────────────────────────────────────────────────

    /** GET /api/learner-journeys/:learnerJourneyId/exam-attempt */
    @GetMapping("/api/learner-journeys/{learnerJourneyId}/exam-attempt")
    public ResponseEntity<ExamAttemptDto> getAttempt(
            @PathVariable String learnerJourneyId) {
        return ResponseEntity.ok(service.getAttemptForCaller(learnerJourneyId));
    }

    /** POST /api/learner-journeys/:learnerJourneyId/exam-attempt */
    @PostMapping("/api/learner-journeys/{learnerJourneyId}/exam-attempt")
    public ResponseEntity<ExamAttemptDto> submitAttempt(
            @PathVariable String learnerJourneyId,
            @Valid @RequestBody SubmitAttemptRequest req) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.submitAttempt(learnerJourneyId, req));
    }

    /** PATCH /api/exam-attempts/:id/grade */
    @PatchMapping("/api/exam-attempts/{id}/grade")
    public ResponseEntity<ExamAttemptDto> gradeAttempt(
            @PathVariable String id,
            @Valid @RequestBody GradeAttemptRequest req) {
        return ResponseEntity.ok(service.gradeAttempt(id, req));
    }
}