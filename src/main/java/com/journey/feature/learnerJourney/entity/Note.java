package com.journey.feature.learnerJourney.entity;

import com.journey.common.enums.UserRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "journey_notes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Note {

    @Id
    @Column(length = 36)
    private String id;

    /** FK to LearnerJourneyItem.id — stored as a plain column (not a JPA join) for simplicity. */
    @Column(name = "learner_journey_item_id", nullable = false, length = 36)
    private String learnerJourneyItemId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "actor_id", length = 36)
    private String actorId;

    @Column(name = "actor_name")
    private String actorName;


    @Column(name = "actor_role", length = 20)
    private Integer actorRole;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
