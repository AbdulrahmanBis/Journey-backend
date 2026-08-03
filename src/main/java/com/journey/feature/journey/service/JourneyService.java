package com.journey.feature.journey.service;

import com.journey.feature.journey.dto.CreateJourneyRequest;
import com.journey.feature.journey.dto.JourneyDto;
import com.journey.feature.journey.dto.JourneyItemDto;
import com.journey.feature.journey.entity.Journey;
import com.journey.feature.journey.entity.JourneyItem;
import com.journey.feature.journey.repository.JourneyItemRepository;
import com.journey.feature.journey.repository.JourneyRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class JourneyService {

    private final JourneyRepository journeyRepository;
    private final JourneyItemRepository journeyItemRepository;

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

    public List<JourneyItemDto> getItemsForJourney(String journeyId) {
        return journeyItemRepository.findByJourneyIdOrderByOrder(journeyId)
                .stream().map(this::toItemDto).toList();
    }

    public List<JourneyItem> getItemEntitiesForJourney(String journeyId) {
        return journeyItemRepository.findByJourneyIdOrderByOrder(journeyId);
    }

    // ─── Mutations ────────────────────────────────────────────────────────────

    @Transactional
    public JourneyDto createJourney(CreateJourneyRequest req) {
        Journey journey = Journey.builder()
                .title(req.title())
                .description(req.description())
                .techTag(req.techTag())
                .createdById(req.createdById())
                .createdByName(req.createdByName())
                .build();
        Journey saved = journeyRepository.save(journey);
        saveItems(saved.getId(), req.items());
        return toDto(saved);
    }

    @Transactional
    public JourneyDto updateJourney(String id, CreateJourneyRequest req) {
        Journey journey = findOrThrow(id);
        journey.setTitle(req.title());
        journey.setDescription(req.description());
        journey.setTechTag(req.techTag());
        journey.setUpdatedAt(LocalDateTime.now());
        Journey saved = journeyRepository.save(journey);

        // Replace items — delete old, insert new in order
        journeyItemRepository.deleteByJourneyId(id);
        saveItems(id, req.items());
        return toDto(saved);
    }

    @Transactional
    public void deleteJourney(String id) {
        Journey journey = findOrThrow(id);
        journeyItemRepository.deleteByJourneyId(id);
        journeyRepository.delete(journey);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void saveItems(String journeyId, List<CreateJourneyRequest.ItemPayload> items) {
        AtomicInteger order = new AtomicInteger(1);
        items.forEach(item -> {
            JourneyItem ji = JourneyItem.builder()
                    .journeyId(journeyId)
                    .title(item.title())
                    .description(item.description())
                    .order(order.getAndIncrement())
                    .build();
            // Preserve existing ID if provided (update scenario)
            if (item.id() != null) ji.setId(item.id());
            journeyItemRepository.save(ji);
        });
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    public JourneyDto toDto(Journey j) {
        return new JourneyDto(j.getId(), j.getTitle(), j.getDescription(), j.getTechTag(),
                j.getCreatedById(), j.getCreatedByName(), j.getCreatedAt(), j.getUpdatedAt());
    }

    public JourneyItemDto toItemDto(JourneyItem i) {
        return new JourneyItemDto(i.getId(), i.getJourneyId(), i.getTitle(), i.getDescription(), i.getOrder());
    }

    private Journey findOrThrow(String id) {
        return journeyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journey not found: " + id));
    }
}
