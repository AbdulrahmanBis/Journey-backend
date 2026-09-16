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

/** One journey in a package, at a 1-based position. */
@Entity
@Table(name = "package_journeys")
@IdClass(PackageJourney.Key.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageJourney {

    @Id
    @Column(name = "package_id", length = 36)
    private String packageId;

    @Id
    @Column(name = "journey_id", length = 36)
    private String journeyId;

    @Column(nullable = false)
    private Integer position;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String packageId;
        private String journeyId;
    }
}
