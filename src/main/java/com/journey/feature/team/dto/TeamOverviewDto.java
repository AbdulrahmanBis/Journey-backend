package com.journey.feature.team.dto;

import java.util.List;

/**
 * @param seniors the seniors in scope, for the table's senior filter (empty for a Senior)
 */
public record TeamOverviewDto(
        TeamKpisDto kpis,
        List<AttentionItemDto> attention,
        List<TeamLearnerDto> learners,
        List<SeniorOptionDto> seniors
) {
    public record SeniorOptionDto(String id, String name, int learnerCount) {}
}
