package com.journey.feature.file.dto;

/** What POST /api/files hands back — the client stores this on the attachment it is building. */
public record UploadedFileDto(
        String storageKey,
        String originalName,
        String mimeType,
        long sizeBytes
) {}
