package com.journey.feature.certificate.entity;

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
 * Proof that a learner completed a journey (exam passed, if it has one) or a whole package. Exactly one of
 * {@code learnerJourneyId} / {@code packageAssignmentId} is set, matching {@code type}.
 */
@Entity
@Table(name = "certificates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Certificate {

    @Id
    @Column(length = 36)
    private String id;

    /** Reference printed on the certificate, e.g. JRN-7K2F-9QXA. */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    /** CatalogItemType: 1001 journey, 1002 package. */
    @Column(nullable = false)
    private Integer type;

    @Column(name = "learner_id", nullable = false, length = 36)
    private String learnerId;

    @Column(name = "learner_journey_id", length = 36)
    private String learnerJourneyId;

    @Column(name = "package_assignment_id", length = 36)
    private String packageAssignmentId;

    /** The journey or package title when it was earned. */
    @Column(nullable = false)
    private String title;

    @Column(name = "exam_score_percent")
    private Integer examScorePercent;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Column(name = "issued_at", nullable = false)
    @Builder.Default
    private LocalDateTime issuedAt = LocalDateTime.now();
}
