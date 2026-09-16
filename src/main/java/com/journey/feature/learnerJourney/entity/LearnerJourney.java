package com.journey.feature.learnerJourney.entity;

import com.journey.common.enums.ItemStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "learner_journeys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearnerJourney {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "journey_id", nullable = false, length = 36)
    private String journeyId;

    @Column(name = "learner_id", nullable = false, length = 36)
    private String learnerId;

    @Column(name = "assigned_by_id", length = 36)
    private String assignedById;

    @Column(name = "assigned_by_name")
    private String assignedByName;

    @Column(name = "assigned_at")
    @Builder.Default
    private LocalDateTime assignedAt = LocalDateTime.now();


    @Column(nullable = false, length = 20)
    private Integer status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
