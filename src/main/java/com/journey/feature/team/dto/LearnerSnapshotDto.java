package com.journey.feature.team.dto;

import java.util.List;

/** One learner's row and open attention items, for the top of their profile page. */
public record LearnerSnapshotDto(
        TeamLearnerDto learner,
        List<AttentionItemDto> attention
) {}
