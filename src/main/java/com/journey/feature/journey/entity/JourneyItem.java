package com.journey.feature.journey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "journey_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyItem {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "journey_id", nullable = false, length = 36)
    private String journeyId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "item_order")
    private Integer order;
}
