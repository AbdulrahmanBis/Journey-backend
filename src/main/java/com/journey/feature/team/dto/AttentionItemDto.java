package com.journey.feature.team.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One thing a staff member should act on. The UI builds the sentence from the parts, in either
 * language; {@code link} is the page where the action happens.
 *
 * @param since when it started waiting (submitted, last activity…), for "3 days ago"
 * @param days  days overdue, or days without activity, where that applies
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttentionItemDto(
        EnumValueDto type,
        String learnerId,
        String learnerName,
        String journeyTitle,
        String itemTitle,
        String link,
        LocalDateTime since,
        LocalDate dueDate,
        Integer days
) {}
