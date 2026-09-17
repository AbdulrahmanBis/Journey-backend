package com.journey.feature.learnerJourney.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A learner's standing on one unit. The unit carries the review workflow (ItemStatus codes): it goes
 * to "waiting for review" when its items and quiz are done, and a reviewer completes it or sends it
 * back. Quiz answers are kept as JSON so a retake simply replaces them.
 */
@Entity
@Table(name = "learner_journey_units")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearnerJourneyUnit {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "learner_journey_id", nullable = false, length = 36)
    private String learnerJourneyId;

    @Column(name = "unit_id", nullable = false, length = 36)
    private String unitId;

    @Column(nullable = false)
    private Integer status;

    /** JSON list of { questionId, selectedOptionIndex, boolAnswer }. */
    @Column(name = "quiz_answers", columnDefinition = "TEXT")
    private String quizAnswers;

    @Column(name = "quiz_score_percent")
    private Integer quizScorePercent;

    @Column(name = "quiz_submitted_at")
    private LocalDateTime quizSubmittedAt;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
