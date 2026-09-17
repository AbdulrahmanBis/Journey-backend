package com.journey.feature.learnerJourney.entity;

import com.journey.common.enums.ItemStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

    /** When the learner should finish; null means no deadline. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Set when the "due soon" reminder went out for the current due date. */
    @Column(name = "due_soon_notified_at")
    private LocalDateTime dueSoonNotifiedAt;

    /** Set when the "overdue" reminder went out for the current due date. */
    @Column(name = "overdue_notified_at")
    private LocalDateTime overdueNotifiedAt;

    /**
     * The learner enrolled from the catalog. assignedBy then holds their reviewer (senior, or a
     * manager of their department), so reviews and notifications still reach a person.
     */
    @Column(name = "self_enrolled", nullable = false)
    @Builder.Default
    private Boolean selfEnrolled = false;


    @Column(nullable = false, length = 20)
    private Integer status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
