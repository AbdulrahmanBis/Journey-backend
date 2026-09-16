package com.journey.common.storage;

/** Metadata the database keeps about an uploaded file — never the bytes themselves. */
public record StoredFile(
        String storageKey,
        String originalName,
        String mimeType,
        long sizeBytes
) {}
