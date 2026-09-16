package com.journey.feature.journeyPackage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * A learner journey covered by a package assignment, frozen in order when the package was assigned
 * — editing the package later does not change it.
 */
@Entity
@Table(name = "package_assignment_journeys")
@IdClass(PackageAssignmentJourney.Key.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageAssignmentJourney {

    @Id
    @Column(name = "package_assignment_id", length = 36)
    private String packageAssignmentId;

    @Id
    @Column(name = "learner_journey_id", length = 36)
    private String learnerJourneyId;

    @Column(nullable = false)
    private Integer position;

    /** False when the learner already had this journey and the package reused it. */
    @Column(name = "created_by_package", nullable = false)
    private Boolean createdByPackage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String packageAssignmentId;
        private String learnerJourneyId;
    }
}
