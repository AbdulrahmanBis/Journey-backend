package com.journey.feature.file.controller;

import com.journey.common.storage.LocalDiskStorageService;
import com.journey.common.storage.StorageService;
import com.journey.common.storage.StoredFile;
import com.journey.feature.file.dto.UploadedFileDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Upload and delivery of attachment files.
 *
 * <p>Delivery supports HTTP Range so uploaded video can be seeked rather than downloaded whole,
 * which is the difference between a player that scrubs and one that stalls.
 */
@RestController
@RequiredArgsConstructor
public class FileController {

    private final StorageService storage;

    /** Only these roles may author journeys, so only these may upload. */
    private static final Set<String> UPLOAD_ROLES =
            Set.of("ROLE_SENIOR", "ROLE_MANAGER", "ROLE_ADMIN");

    /** Extensions are assigned by us from an allowlisted MIME, so this reverse map is closed. */
    private static final Map<String, MediaType> MIME_BY_EXT = Map.ofEntries(
            Map.entry("png", MediaType.IMAGE_PNG),
            Map.entry("jpg", MediaType.IMAGE_JPEG),
            Map.entry("gif", MediaType.IMAGE_GIF),
            Map.entry("webp", MediaType.parseMediaType("image/webp")),
            Map.entry("svg", MediaType.parseMediaType("image/svg+xml")),
            Map.entry("pdf", MediaType.APPLICATION_PDF),
            Map.entry("doc", MediaType.parseMediaType("application/msword")),
            Map.entry("docx", MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("xls", MediaType.parseMediaType("application/vnd.ms-excel")),
            Map.entry("xlsx", MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            Map.entry("txt", MediaType.TEXT_PLAIN),
            Map.entry("mp4", MediaType.parseMediaType("video/mp4")),
            Map.entry("webm", MediaType.parseMediaType("video/webm")),
            Map.entry("mov", MediaType.parseMediaType("video/quicktime"))
    );

    /** Kinds safe to render in place; everything else is forced to download. */
    private static final Set<String> INLINE_EXT =
            Set.of("png", "jpg", "gif", "webp", "pdf", "mp4", "webm", "mov");

    /** POST /api/files — multipart upload, returns the key to attach. */
    @PostMapping("/api/files")
    public ResponseEntity<UploadedFileDto> upload(@RequestPart("file") MultipartFile file) {
        requireUploadRole();
        StoredFile stored = storage.store(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(new UploadedFileDto(
                stored.storageKey(), stored.originalName(), stored.mimeType(), stored.sizeBytes()));
    }

    /**
     * GET /api/files/{key} — serves the bytes, honouring Range.
     *
     * <p>The key contains slashes ({@code 2026/09/<uuid>.mp4}), hence the wildcard mapping. The
     * storage layer validates the key shape and confirms it resolves inside the storage root, so a
     * crafted path cannot escape.
     *
     * <p>The range is streamed straight to the response rather than returned as a
     * {@code ResourceRegion}: that converter only participates in Spring's generic write path, so a
     * wildcard return type makes it refuse the body and the request fails with 500. Writing the
     * bytes here is deterministic and keeps seeking working for every media type.
     */
    @GetMapping("/api/files/**")
    public void download(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String key = extractKey(request);
        String ext = key.substring(key.lastIndexOf('.') + 1).toLowerCase();

        MediaType mediaType = MIME_BY_EXT.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM);
        Resource resource = storage.load(key);
        long length = storage.sizeOf(key);

        boolean inline = INLINE_EXT.contains(ext)
                && !LocalDiskStorageService.NEVER_INLINE.contains(mediaType.toString());

        response.setContentType(mediaType.toString());
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, inline ? "inline" : "attachment");
        // Keys are UUID-based, so a given key's bytes never change — safe to cache hard.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=31536000, private");

        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        if (rangeHeader == null || rangeHeader.isBlank()) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentLengthLong(length);
            try (InputStream in = resource.getInputStream()) {
                in.transferTo(response.getOutputStream());
            }
            return;
        }

        long start;
        long end;
        try {
            // HttpRange handles suffix forms like "bytes=-500" correctly.
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            if (ranges.isEmpty()) throw new IllegalArgumentException("no ranges");
            HttpRange first = ranges.get(0);
            start = first.getRangeStart(length);
            end = first.getRangeEnd(length);
        } catch (IllegalArgumentException e) {
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + length);
            response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
            return;
        }

        if (start >= length) {
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + length);
            response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
            return;
        }
        end = Math.min(end, length - 1);
        long count = end - start + 1;

        response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
        response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length);
        response.setContentLengthLong(count);

        try (InputStream in = resource.getInputStream()) {
            long skipped = 0;
            while (skipped < start) {
                long s = in.skip(start - skipped);
                if (s <= 0) break;
                skipped += s;
            }
            byte[] buffer = new byte[64 * 1024];
            long remaining = count;
            var out = response.getOutputStream();
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) break;
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    private String extractKey(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (path == null) path = request.getRequestURI();
        int idx = path.indexOf("/api/files/");
        String key = idx >= 0 ? path.substring(idx + "/api/files/".length()) : "";
        if (key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file key supplied.");
        }
        return key;
    }

    /**
     * SecurityConfig only requires authentication, not a role, so the check lives here — uploads
     * are the one place where a plain learner account could otherwise write to the filesystem.
     */
    private void requireUploadRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> UPLOAD_ROLES.contains(a.getAuthority()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your role is not allowed to upload files.");
        }
    }
}
