package com.journey.feature.team.dto;

/**
 * @param awaitingReview    exams to grade plus items waiting for a reviewer
 * @param completedThisMonth journeys completed since the first of this month
 */
public record TeamKpisDto(
        int learners,
        int onTrack,
        int atRisk,
        int overdue,
        int awaitingReview,
        int completedThisMonth
) {}
