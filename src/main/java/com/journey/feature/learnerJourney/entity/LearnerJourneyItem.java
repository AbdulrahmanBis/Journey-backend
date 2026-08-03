package com.journey.feature.learnerJourney.entity;

import com.journey.common.enums.ItemStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "learner_journey_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearnerJourneyItem {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "learner_journey_id", nullable = false, length = 36)
    private String learnerJourneyId;

    @Column(name = "journey_item_id", nullable = false, length = 36)
    private String journeyItemId;

    @Column(nullable = false, length = 20)
    private Integer status;

    @Column(name = "time_spent_hours")
    private Double timeSpentHours;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "learner_journey_item_id")
    @Builder.Default
    private List<Note> notes = new ArrayList<>();
}
