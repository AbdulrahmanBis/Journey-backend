package com.journey.feature.journeyPackage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A package given to a learner. Its status isn't stored: it follows from the journeys it covers,
 * except cancellation, which is a decision rather than a result.
 */
@Entity
@Table(name = "package_assignments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageAssignment {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "package_id", nullable = false, length = 36)
    private String packageId;

    @Column(name = "learner_id", nullable = false, length = 36)
    private String learnerId;

    @Column(name = "assigned_by_id", length = 36)
    private String assignedById;

    @Column(name = "assigned_by_name")
    private String assignedByName;

    @Column(name = "assigned_at", nullable = false)
    @Builder.Default
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Enrolled from the catalog; assignedBy then holds the reviewer, as on LearnerJourney. */
    @Column(name = "self_enrolled", nullable = false)
    @Builder.Default
    private Boolean selfEnrolled = false;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
}
