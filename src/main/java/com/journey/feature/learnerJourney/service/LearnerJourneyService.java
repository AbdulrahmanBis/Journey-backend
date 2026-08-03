package com.journey.feature.learnerJourney.service;

import com.journey.common.enums.ItemStatus;
import com.journey.common.enums.UserRole;
import com.journey.feature.journey.entity.Journey;
import com.journey.feature.journey.entity.JourneyItem;
import com.journey.feature.journey.service.JourneyService;
import com.journey.feature.learnerJourney.dto.*;
import com.journey.feature.learnerJourney.entity.LearnerJourney;
import com.journey.feature.learnerJourney.entity.LearnerJourneyItem;
import com.journey.feature.learnerJourney.entity.Note;
import com.journey.feature.learnerJourney.repository.LearnerJourneyItemRepository;
import com.journey.feature.learnerJourney.repository.LearnerJourneyRepository;
import com.journey.feature.learnerJourney.repository.NoteRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LearnerJourneyService {

    private final LearnerJourneyRepository learnerJourneyRepository;
    private final LearnerJourneyItemRepository learnerJourneyItemRepository;
    private final NoteRepository noteRepository;
    private final JourneyService journeyService;

    // ─── Queries ──────────────────────────────────────────────────────────────

    /**
     * GET /api/learner-journeys?learnerId=x
     * Returns all journey views for a given learner.
     */
    public List<LearnerJourneyViewDto> getLearnerJourneyViews(String learnerId) {
        return learnerJourneyRepository.findByLearnerId(learnerId).stream()
                .map(this::buildView)
                .toList();
    }

    /**
     * GET /api/learner-journeys/:id
     * Returns the fully composed view for one LearnerJourney row.
     * This mirrors the JS /api/journeylog/:id — joins journey template, items, and per-learner progress.
     */
    public LearnerJourneyViewDto getLearnerJourneyView(String id) {
        LearnerJourney lj = findLjOrThrow(id);
        return buildView(lj);
    }

    // ─── Mutations ────────────────────────────────────────────────────────────

    /**
     * POST /api/learner-journeys
     * Assign a journey template to a learner and auto-create a LearnerJourneyItem row for each template item.
     * Mirrors the JS assignJourney logic.
     */
    @Transactional
    public LearnerJourneyViewDto assignJourney(AssignJourneyRequest req) {
        if (learnerJourneyRepository.existsByJourneyIdAndLearnerId(req.journeyId(), req.learnerId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This learner is already assigned to that journey.");
        }

        LearnerJourney lj = LearnerJourney.builder()
                .journeyId(req.journeyId())
                .learnerId(req.learnerId())
                .assignedById(req.assignedById())
                .assignedByName(req.assignedByName())
                .status(ItemStatus.NEW.getCode())
                .build();
        LearnerJourney saved = learnerJourneyRepository.save(lj);

        // Auto-create one LearnerJourneyItem per template item — status NEW
        List<JourneyItem> templateItems = journeyService.getItemEntitiesForJourney(req.journeyId());
        templateItems.forEach(item -> {
            LearnerJourneyItem lji = LearnerJourneyItem.builder()
                    .learnerJourneyId(saved.getId())
                    .journeyItemId(item.getId())
                    .status(ItemStatus.NEW.getCode())
                    .build();
            learnerJourneyItemRepository.save(lji);
        });

        return buildView(saved);
    }

    /**
     * PATCH /api/learner-journeys/:id/status
     */
    @Transactional
    public LearnerJourneyViewDto updateJourneyStatus(String id, UpdateJourneyStatusRequest req) {
        LearnerJourney lj = findLjOrThrow(id);
        lj.setStatus(ItemStatus.fromEnglish(req.status()).getCode());
        if (req.status().equals(ItemStatus.COMPLETED.getEnglish()) && lj.getCompletedAt() == null) {
            lj.setCompletedAt(LocalDateTime.now());
        }
        learnerJourneyRepository.save(lj);
        return buildView(lj);
    }

    /**
     * PATCH /api/learner-journey-items/:itemId/status
     * Mirrors the JS updatejurneyItemStatus logic.
     */
    @Transactional
    public LearnerJourneyItemDto updateItemStatus(String itemId, UpdateItemStatusRequest req) {
        LearnerJourneyItem item = findLjiOrThrow(itemId);

        if (req.status().equals(ItemStatus.COMPLETED.getEnglish()) &&
                (req.timeSpentHours() == null || req.timeSpentHours() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "timeSpentHours is required when marking an item completed.");
        }

        item.setStatus(ItemStatus.fromEnglish(req.status()).getCode());
        item.setUpdatedAt(LocalDateTime.now());
        if (req.timeSpentHours() != null) {
            item.setTimeSpentHours(req.timeSpentHours());
        }

        LearnerJourneyItem saved = learnerJourneyItemRepository.save(item);

        // Auto-update parent LearnerJourney status based on item completions
        recomputeJourneyStatus(item.getLearnerJourneyId());

        return toItemDto(saved);
    }

    /**
     * POST /api/learner-journey-items/:itemId/notes
     * Mirrors the JS addNote logic.
     */
    @Transactional
    public NoteDto addNote(String itemId, AddNoteRequest req) {
        // Verify the item exists
        findLjiOrThrow(itemId);

        Note note = Note.builder()
                .learnerJourneyItemId(itemId)
                .message(req.message())
                .actorId(req.actorId())
                .actorName(req.actorName())
                .actorRole(UserRole.fromEnglish(req.actorRole()).getCode())
                .build();

        return toNoteDto(noteRepository.save(note));
    }

    // ─── View builder (core JS join logic) ───────────────────────────────────

    /**
     * Builds the fully composed LearnerJourneyViewDto:
     *   1. Load the Journey template and its items (ordered)
     *   2. For each template item, find the learner's LearnerJourneyItem (progress)
     *   3. Compute percentComplete and totalTimeSpentHours
     *
     * This reproduces exactly what the JS /api/journeylog/:id and
     * /api/learnersJourneysBySenior/:seniorId endpoints compute inline.
     */
    public LearnerJourneyViewDto buildView(LearnerJourney lj) {
        Journey journey = journeyService.getEntityById(lj.getJourneyId());
        List<JourneyItem> templateItems = journeyService.getItemEntitiesForJourney(lj.getJourneyId());
        List<LearnerJourneyItem> progressRows =
                learnerJourneyItemRepository.findByLearnerJourneyId(lj.getId());

        // Compose items — join template item with the matching progress row
        List<JourneyItemWithProgressDto> itemViews = templateItems.stream()
                .map(ti -> {
                    LearnerJourneyItem progress = progressRows.stream()
                            .filter(p -> p.getJourneyItemId().equals(ti.getId()))
                            .findFirst()
                            .orElse(null);

                    // Fallback: if no progress row exists (shouldn't happen after assign) create a virtual one
                    LearnerJourneyItemDto progressDto = progress != null
                            ? toItemDto(progress)
                            : new LearnerJourneyItemDto(
                            null, lj.getId(), ti.getId(),
                            ItemStatus.NEW.getEnglish(), null, lj.getAssignedAt(), List.of()
                    );

                    return new JourneyItemWithProgressDto(
                            ti.getId(), ti.getJourneyId(), ti.getTitle(),
                            ti.getDescription(), ti.getOrder(), progressDto);
                })
                .toList();

        // Compute percentComplete — completed items / total items * 100
        long completedCount = itemViews.stream()
                .filter(i -> i.progress() != null && i.progress().status().equals(ItemStatus.COMPLETED.getEnglish()))
                .count();
        int percentComplete = itemViews.isEmpty()
                ? 0
                : (int) Math.round((double) completedCount / itemViews.size() * 100);

        // Compute totalTimeSpentHours — sum of completed items only (matches JS logic)
        double totalHours = itemViews.stream()
                .filter(i -> i.progress() != null && i.progress().status().equals(ItemStatus.COMPLETED.getEnglish()))
                .mapToDouble(i -> i.progress().timeSpentHours() != null ? i.progress().timeSpentHours() : 0.0)
                .sum();
        totalHours = Math.round(totalHours * 10.0) / 10.0;

        return new LearnerJourneyViewDto(
                lj.getId(), lj.getJourneyId(), lj.getLearnerId(),
                lj.getAssignedById(), lj.getAssignedByName(), lj.getAssignedAt(),
                ItemStatus.fromCode(lj.getStatus()).getEnglish(), lj.getStartedAt(), lj.getCompletedAt(),
                journeyService.toDto(journey),
                itemViews,
                percentComplete,
                totalHours
        );
    }

    // ─── Auto-status promotion ────────────────────────────────────────────────

    private void recomputeJourneyStatus(String learnerJourneyId) {
        LearnerJourney lj = findLjOrThrow(learnerJourneyId);
        if (lj.getStatus() == ItemStatus.CANCELLED.getCode()) return;

        List<LearnerJourneyItem> items =
                learnerJourneyItemRepository.findByLearnerJourneyId(learnerJourneyId);
        if (items.isEmpty()) return;

        boolean allCompleted = items.stream().allMatch(i -> i.getStatus() == ItemStatus.COMPLETED.getCode());
        boolean anyStarted = items.stream().anyMatch(i -> i.getStatus() != ItemStatus.NEW.getCode());

        if (allCompleted) {
            lj.setStatus(ItemStatus.COMPLETED.getCode());
            if (lj.getCompletedAt() == null) lj.setCompletedAt(LocalDateTime.now());
        } else if (anyStarted && lj.getStatus() == ItemStatus.NEW.getCode()) {
            lj.setStatus(ItemStatus.REFLECT.getCode());
            if (lj.getStartedAt() == null) lj.setStartedAt(LocalDateTime.now());
        }
        learnerJourneyRepository.save(lj);
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    public LearnerJourneyItemDto toItemDto(LearnerJourneyItem item) {
        List<NoteDto> notes = item.getNotes().stream().map(this::toNoteDto).toList();
        return new LearnerJourneyItemDto(
                item.getId(), item.getLearnerJourneyId(), item.getJourneyItemId(),
                ItemStatus.fromCode(item.getStatus()).getEnglish(), item.getTimeSpentHours(), item.getUpdatedAt(), notes);
    }

    public NoteDto toNoteDto(Note n) {
        return new NoteDto(n.getId(), n.getActorId(), n.getActorName(),
                UserRole.fromCode(n.getActorRole()).getEnglish(), n.getMessage(), n.getTimestamp());
    }

    // ─── Internal finders ─────────────────────────────────────────────────────

    private LearnerJourney findLjOrThrow(String id) {
        return learnerJourneyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "LearnerJourney not found: " + id));
    }

    private LearnerJourneyItem findLjiOrThrow(String id) {
        return learnerJourneyItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "LearnerJourneyItem not found: " + id));
    }
}
