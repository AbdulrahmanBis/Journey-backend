package com.journey.common.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Where uploaded bytes live.
 *
 * <p>This is the seam for swapping in the real file server later: the database only ever stores the
 * opaque {@code storageKey} this returns, so replacing the implementation needs no schema change and
 * no API change. {@link LocalDiskStorageService} is the default so the feature works today.
 */
public interface StorageService {

    /**
     * Persists the upload and returns its metadata.
     *
     * @throws com.journey.common.error.ApiException 400 when the type or size is
     *         not allowed — callers should not have to pre-validate.
     */
    StoredFile store(MultipartFile file);

    /** Resolves a key back to readable bytes. Must reject any key it did not issue. */
    Resource load(String storageKey);

    /** Size in bytes, used to answer Range requests without reading the whole file. */
    long sizeOf(String storageKey);

    void delete(String storageKey);
}
