package com.journey.feature.journey.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.journey.common.dto.EnumValueDto;

/**
 * An attachment as the client sees it.
 *
 * <p>Exactly one of {@code url} (external kinds) or {@code storageKey} (uploaded kinds) is set.
 * For uploaded kinds the client builds its own src from the key, so the backend never hands out a
 * path it would have to keep in sync.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttachmentDto(
        String id,
        EnumValueDto kind,
        String label,
        String url,
        String storageKey,
        String mimeType,
        Long sizeBytes,
        String originalName,
        Integer order
) {}
