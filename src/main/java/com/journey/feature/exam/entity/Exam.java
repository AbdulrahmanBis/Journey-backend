package com.journey.feature.exam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "exams")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Exam {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "journey_id", nullable = false)
    private String journeyId;

    @Column(nullable = false)
    private String title;

    @Column(name = "passing_score_percent", nullable = false)
    private Integer passingScorePercent;

    @Column(name = "created_by_id", nullable = false)
    private String createdById;

    @Column(name = "created_by_name", nullable = false)
    private String createdByName;


    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}