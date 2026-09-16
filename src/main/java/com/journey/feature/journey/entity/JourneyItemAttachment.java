package com.journey.feature.journey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One piece of content attached to a journey item — a link, an uploaded file, or an embed.
 *
 * <p>Binary data is never stored here. Uploaded kinds keep a {@code storageKey} that the
 * StorageService resolves; external kinds keep a {@code url}. That split is what lets the storage
 * backend be swapped without touching the schema.
 */
@Entity
@Table(name = "journey_item_attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyItemAttachment {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "journey_item_id", nullable = false, length = 36)
    private String journeyItemId;

    /** AttachmentKind code (1001-1007). */
    @Column(nullable = false)
    private Integer kind;

    /** What the learner sees as the attachment's name. */
    @Column(name = "label")
    private String label;

    /** External kinds only — link / video embed / iframe. */
    @Column(name = "url", length = 1000)
    private String url;

    /** Uploaded kinds only — opaque key resolved by the StorageService. */
    @Column(name = "storage_key", length = 255)
    private String storageKey;

    @Column(name = "mime_type", length = 127)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** Original upload filename, kept for the download prompt. */
    @Column(name = "original_name")
    private String originalName;

    @Column(name = "item_order")
    private Integer order;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
