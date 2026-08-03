package com.journey.feature.journey.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateJourneyRequest(
        @NotBlank String title,
        String description,
        String techTag,
        String createdById,
        String createdByName,
       @NotEmpty List<ItemPayload> items
) {
    public record ItemPayload(
            String id,          // present on update (reuse existing), null on create
            @NotBlank String title,
            String description
    ) {}
}