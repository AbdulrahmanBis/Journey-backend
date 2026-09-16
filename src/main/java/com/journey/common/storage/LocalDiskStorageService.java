package com.journey.common.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores uploads on the local filesystem under {@code app.storage.local.root}.
 *
 * <p>Deliberately the simplest thing that works, so the feature is usable before the real file
 * server is wired up. Swapping it out means writing another {@link StorageService} — nothing else
 * changes, because the database only holds the opaque key.
 */
@Slf4j
@Service
public class LocalDiskStorageService implements StorageService {

    /** Keys we issue look like {@code 2026/09/<uuid>.<ext>} — anything else is rejected on read. */
    private static final Pattern KEY_PATTERN =
            Pattern.compile("^\\d{4}/\\d{2}/[0-9a-f-]{36}\\.[a-z0-9]{1,8}$");

    /**
     * Allowlist rather than a blocklist: anything not named here cannot be uploaded. Keeps
     * executables, scripts and HTML (a stored-XSS vector when served back) out entirely.
     */
    private static final Map<String, String> ALLOWED = Map.ofEntries(
            Map.entry("image/png", "png"),
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/gif", "gif"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/svg+xml", "svg"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.ms-excel", "xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
            Map.entry("text/plain", "txt"),
            Map.entry("video/mp4", "mp4"),
            Map.entry("video/webm", "webm"),
            Map.entry("video/quicktime", "mov")
    );

    /** SVG can carry script, so it is stored but always served as a download, never inline. */
    public static final Set<String> NEVER_INLINE = Set.of("image/svg+xml", "text/plain");

    private final Path root;
    private final long maxBytes;

    public LocalDiskStorageService(
            @Value("${app.storage.local.root:./storage}") String rootDir,
            @Value("${app.storage.max-file-size-bytes:209715200}") long maxBytes) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        try {
            Files.createDirectories(this.root);
            log.info("File storage root: {}", this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create storage root: " + this.root, e);
        }
    }

    @Override
    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file supplied.");
        }
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "File is larger than the " + (maxBytes / 1024 / 1024) + "MB limit.");
        }

        String mime = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT).split(";")[0].trim();
        String ext = ALLOWED.get(mime);
        if (ext == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type: " + (mime.isEmpty() ? "unknown" : mime));
        }

        LocalDate today = LocalDate.now();
        String key = "%04d/%02d/%s.%s".formatted(
                today.getYear(), today.getMonthValue(), UUID.randomUUID(), ext);

        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed writing upload to {}", target, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the file.");
        }

        return new StoredFile(key, sanitizeName(file.getOriginalFilename()), mime, file.getSize());
    }

    @Override
    public Resource load(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isReadable(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found.");
        }
        return new FileSystemResource(path);
    }

    @Override
    public long sizeOf(String storageKey) {
        try {
            return Files.size(resolve(storageKey));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found.");
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            log.warn("Could not delete {}", storageKey, e);
        }
    }

    /**
     * Validates the key shape and confirms the resolved path stays inside the storage root — the
     * shape check alone would not stop a crafted key, so both are enforced.
     */
    private Path resolve(String storageKey) {
        if (storageKey == null || !KEY_PATTERN.matcher(storageKey).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed file key.");
        }
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed file key.");
        }
        return path;
    }

    /** Filenames come from the client, so strip any path parts before echoing one back. */
    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) return "file";
        String base = Paths.get(name).getFileName().toString();
        return base.length() > 180 ? base.substring(base.length() - 180) : base;
    }
}
