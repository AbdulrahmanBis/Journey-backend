package com.journey.feature.journey.service;

import com.journey.common.enums.AttachmentKind;
import com.journey.common.security.AccessPolicy;
import com.journey.feature.user.entity.User;
import com.journey.common.service.IdGeneratorService;
import com.journey.common.storage.StorageService;
import com.journey.feature.journey.dto.AttachmentDto;
import com.journey.feature.journey.dto.CreateJourneyRequest;
import com.journey.feature.journey.dto.JourneyDto;
import com.journey.feature.journey.dto.JourneyItemDto;
import com.journey.feature.journey.entity.Journey;
import com.journey.feature.journey.entity.JourneyItem;
import com.journey.feature.journey.entity.JourneyItemAttachment;
import com.journey.feature.journey.repository.JourneyItemAttachmentRepository;
import com.journey.feature.journey.repository.JourneyItemRepository;
import com.journey.feature.journey.repository.JourneyRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class JourneyService {

    private final JourneyRepository journeyRepository;
    private final JourneyItemRepository journeyItemRepository;
    private final JourneyItemAttachmentRepository attachmentRepository;
    private final IdGeneratorService idGenerator;
    private final StorageService storage;
    private final AccessPolicy access;

    // ─── Queries ──────────────────────────────────────────────────────────────

    public List<JourneyDto> getAllJourneys() {
        return journeyRepository.findAll().stream().map(this::toDto).toList();
    }

    public JourneyDto getById(String id) {
        return toDto(findOrThrow(id));
    }

    public Journey getEntityById(String id) {
        return findOrThrow(id);
    }

    /** Items with their attachments. Attachments are fetched in one query, not per item. */
    public List<JourneyItemDto> getItemsForJourney(String journeyId) {
        List<JourneyItem> items = journeyItemRepository.findByJourneyIdOrderByOrder(journeyId);
        if (items.isEmpty()) return List.of();

        Map<String, List<JourneyItemAttachment>> byItem = attachmentRepository
                .findByJourneyItemIdIn(items.stream().map(JourneyItem::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(JourneyItemAttachment::getJourneyItemId));

        return items.stream()
                .map(i -> toItemDto(i, byItem.getOrDefault(i.getId(), List.of())))
                .toList();
    }

    public List<JourneyItem> getItemEntitiesForJourney(String journeyId) {
        return journeyItemRepository.findByJourneyIdOrderByOrder(journeyId);
    }

    // ─── Mutations ────────────────────────────────────────────────────────────

    @Transactional
    public JourneyDto createJourney(CreateJourneyRequest req) {
        // Journeys are company-wide; any staff role may author one. The author is the caller.
        User author = access.requireRole(AccessPolicy.STAFF);
        Journey journey = Journey.builder()
                .id(idGenerator.next(IdGeneratorService.JOURNEY, "j-"))
                .title(req.title())
                .description(req.description())
                .techTag(req.techTag())
                .createdById(author.getId())
                .createdByName(author.getName())
                .build();
        Journey saved = journeyRepository.save(journey);
        saveItems(saved.getId(), req.items());
        return toDto(saved);
    }

    @Transactional
    public JourneyDto updateJourney(String id, CreateJourneyRequest req) {
        access.requireRole(AccessPolicy.STAFF);
        Journey journey = findOrThrow(id);
        journey.setTitle(req.title());
        journey.setDescription(req.description());
        journey.setTechTag(req.techTag());
        journey.setUpdatedAt(LocalDateTime.now());
        Journey saved = journeyRepository.save(journey);

        /*
          Items are replaced wholesale. Attachments live in their own table with no cascade, so
          they must be cleared explicitly or they would be orphaned by the delete below.

          Only the files the author actually dropped may be deleted from storage. An edit re-sends
          the attachments it is keeping, storage key and all, so deleting every file here — as this
          once did — left those rows pointing at bytes that no longer existed, and merely renaming a
          journey broke every attachment in it.
         */
        clearAttachmentsFor(getItemEntitiesForJourney(id), retainedKeys(req));
        journeyItemRepository.deleteByJourneyId(id);
        saveItems(id, req.items());
        return toDto(saved);
    }

    @Transactional
    public void deleteJourney(String id) {
        access.requireRole(AccessPolicy.STAFF);
        Journey journey = findOrThrow(id);
        // The journey is going away, so no file is retained.
        clearAttachmentsFor(getItemEntitiesForJourney(id), Set.of());
        journeyItemRepository.deleteByJourneyId(id);
        journeyRepository.delete(journey);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void saveItems(String journeyId, List<CreateJourneyRequest.ItemPayload> items) {
        AtomicInteger order = new AtomicInteger(1);
        items.forEach(item -> {
            String itemId = item.id() != null
                    ? item.id()
                    : idGenerator.next(IdGeneratorService.JOURNEY_ITEM, "ji-");

            JourneyItem ji = JourneyItem.builder()
                    .id(itemId)
                    .journeyId(journeyId)
                    .title(item.title())
                    .description(item.description())
                    .order(order.getAndIncrement())
                    .build();
            journeyItemRepository.save(ji);

            saveAttachments(itemId, item.attachments());
        });
    }

    /** Attachments arrive as the complete set for an item, so the existing rows are replaced. */
    private void saveAttachments(String itemId, List<CreateJourneyRequest.AttachmentPayload> payloads) {
        attachmentRepository.deleteByJourneyItemId(itemId);
        if (payloads == null || payloads.isEmpty()) return;

        int order = 1;
        for (CreateJourneyRequest.AttachmentPayload p : payloads) {
            AttachmentKind kind = resolveKind(p.kind());
            validate(kind, p);

            attachmentRepository.save(JourneyItemAttachment.builder()
                    .id(p.id() != null
                            ? p.id()
                            : idGenerator.next(IdGeneratorService.JOURNEY_ITEM_ATTACHMENT, "jia-"))
                    .journeyItemId(itemId)
                    .kind(kind.getCode())
                    .label(p.label())
                    .url(kind.isUploaded() ? null : p.url())
                    .storageKey(kind.isUploaded() ? p.storageKey() : null)
                    .mimeType(p.mimeType())
                    .sizeBytes(p.sizeBytes())
                    .originalName(p.originalName())
                    .order(order++)
                    .build());
        }
    }

    /**
     * Uploaded kinds need a storage key; external kinds need an http(s) URL. Rejecting anything
     * else here keeps unusable rows — and javascript: URLs — out of the database.
     */
    private void validate(AttachmentKind kind, CreateJourneyRequest.AttachmentPayload p) {
        if (kind.isUploaded()) {
            if (p.storageKey() == null || p.storageKey().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        kind.getEnglish() + " attachments need an uploaded file.");
            }
            return;
        }

        String url = p.url() == null ? "" : p.url().trim().toLowerCase(Locale.ROOT);
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    kind.getEnglish() + " attachments need an http or https URL.");
        }
    }

    private AttachmentKind resolveKind(Integer code) {
        try {
            return AttachmentKind.fromCode(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown attachment kind: " + code);
        }
    }

    /** Every storage key the incoming request still refers to. */
    private Set<String> retainedKeys(CreateJourneyRequest req) {
        return req.items().stream()
                .flatMap(i -> i.attachments() == null ? Stream.<CreateJourneyRequest.AttachmentPayload>empty()
                                                      : i.attachments().stream())
                .map(CreateJourneyRequest.AttachmentPayload::storageKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Removes attachment rows, and the stored bytes behind them except for keys listed in
     * {@code retained}, so dropped uploads do not pile up and kept ones survive an edit.
     */
    private void clearAttachmentsFor(List<JourneyItem> items, Set<String> retained) {
        for (JourneyItem item : items) {
            List<JourneyItemAttachment> existing =
                    attachmentRepository.findByJourneyItemIdOrderByOrder(item.getId());
            for (JourneyItemAttachment a : existing) {
                if (a.getStorageKey() != null && !retained.contains(a.getStorageKey())) {
                    storage.delete(a.getStorageKey());
                }
            }
            attachmentRepository.deleteByJourneyItemId(item.getId());
        }
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    public JourneyDto toDto(Journey j) {
        return new JourneyDto(j.getId(), j.getTitle(), j.getDescription(), j.getTechTag(),
                j.getCreatedById(), j.getCreatedByName(), j.getCreatedAt(), j.getUpdatedAt());
    }

    public JourneyItemDto toItemDto(JourneyItem i) {
        return toItemDto(i, attachmentRepository.findByJourneyItemIdOrderByOrder(i.getId()));
    }

    private JourneyItemDto toItemDto(JourneyItem i, List<JourneyItemAttachment> attachments) {
        return new JourneyItemDto(
                i.getId(), i.getJourneyId(), i.getTitle(), i.getDescription(), i.getOrder(),
                attachments.stream()
                        .sorted(Comparator.comparing(JourneyItemAttachment::getOrder,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(this::toAttachmentDto)
                        .toList());
    }

    private AttachmentDto toAttachmentDto(JourneyItemAttachment a) {
        return new AttachmentDto(
                a.getId(),
                AttachmentKind.fromCode(a.getKind()).toDto(),
                a.getLabel(),
                a.getUrl(),
                a.getStorageKey(),
                a.getMimeType(),
                a.getSizeBytes(),
                a.getOriginalName(),
                a.getOrder());
    }

    private Journey findOrThrow(String id) {
        return journeyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journey not found: " + id));
    }
}
