package com.journey.feature.journey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A section of a journey: its items in order, and optionally a short quiz at the end. */
@Entity
@Table(name = "journey_units")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyUnit {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "journey_id", nullable = false, length = 36)
    private String journeyId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "unit_order", nullable = false)
    private Integer order;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
