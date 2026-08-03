package com.journey.feature.exam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam_attempts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamAttempt {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "learner_journey_id", nullable = false)
    private String learnerJourneyId;

    @Column(name = "exam_id", nullable = false)
    private String examId;

    @Column(nullable = false)
    private Integer status;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    @Column(name = "graded_by_id")
    private String gradedById;

    @Column(name = "graded_by_name")
    private String gradedByName;

    @Column(name = "score_percent")
    private Integer scorePercent;

    private Boolean passed;
}