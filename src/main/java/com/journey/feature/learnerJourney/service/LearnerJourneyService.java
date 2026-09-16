package com.journey.feature.learnerJourney.service;

import com.journey.common.enums.ItemStatus;
import com.journey.common.enums.UserRole;
import com.journey.common.service.IdGeneratorService;
import com.journey.feature.exam.dto.ExamAttemptDto;
import com.journey.feature.exam.dto.ExamDto;
import com.journey.feature.exam.service.ExamService;
import com.journey.feature.journey.dto.JourneyItemDto;
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
    private final ExamService examService;
    private final IdGeneratorService idGenerator;

    // ─── Queries ──────────────────────────────────────────────────────────────

    /** GET /api/learner-journeys?learnerId=x */
    public List<LearnerJourneyViewDto> getLearnerJourneyViews(String learnerId) {
        return learnerJourneyRepository.findByLearnerId(learnerId).stream()
                .map(this::buildView)
                .toList();
    }

    /** GET /api/learner-journeys/:id — the fully composed quest-log view. */
    public LearnerJourneyViewDto getLearnerJourneyView(String id) {
        LearnerJourney lj = findLjOrThrow(id);
        return buildView(lj);
    }

    // ─── Mutations ────────────────────────────────────────────────────────────

    /**
     * POST /api/learner-journeys
     * Assign a journey template to a learner and auto-create a LearnerJourneyItem row for each item.
     */
    @Transactional
    public LearnerJourneyViewDto assignJourney(AssignJourneyRequest req) {
        if (learnerJourneyRepository.existsByJourneyIdAndLearnerId(req.journeyId(), req.learnerId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This learner is already assigned to that journey.");
        }

        LearnerJourney lj = LearnerJourney.builder()
                .id(idGenerator.next(IdGeneratorService.LEARNER_JOURNEY, "lj-"))
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
                    .id(idGenerator.next(IdGeneratorService.LEARNER_JOURNEY_ITEM, "lji-"))
                    .learnerJourneyId(saved.getId())
                    .journeyItemId(item.getId())
                    .status(ItemStatus.NEW.getCode())
                    .build();
            learnerJourneyItemRepository.save(lji);
        });

        return buildView(saved);
    }

    /** PATCH /api/learner-journeys/:id/status */
    @Transactional
    public LearnerJourneyViewDto updateJourneyStatus(String id, UpdateJourneyStatusRequest req) {
        LearnerJourney lj = findLjOrThrow(id);
        ItemStatus status = resolveStatus(req.status());

        lj.setStatus(status.getCode());
        if (status == ItemStatus.COMPLETED && lj.getCompletedAt() == null) {
            lj.setCompletedAt(LocalDateTime.now());
        }
        learnerJourneyRepository.save(lj);
        return buildView(lj);
    }

    /** PATCH /api/learner-journey-items/:itemId/status */
    @Transactional
    public LearnerJourneyItemDto updateItemStatus(String itemId, UpdateItemStatusRequest req) {
        LearnerJourneyItem item = findLjiOrThrow(itemId);
        ItemStatus status = resolveStatus(req.status());

        if (status == ItemStatus.COMPLETED
                && (req.timeSpentHours() == null || req.timeSpentHours() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "timeSpentHours is required when marking an item completed.");
        }

        item.setStatus(status.getCode());
        item.setUpdatedAt(LocalDateTime.now());
        if (req.timeSpentHours() != null) {
            item.setTimeSpentHours(req.timeSpentHours());
        }

        LearnerJourneyItem saved = learnerJourneyItemRepository.save(item);

        // Auto-update parent LearnerJourney status based on item completions
        recomputeJourneyStatus(item.getLearnerJourneyId());

        return toItemDto(saved);
    }

    /** POST /api/learner-journey-items/:itemId/notes */
    @Transactional
    public NoteDto addNote(String itemId, AddNoteRequest req) {
        findLjiOrThrow(itemId);

        UserRole actorRole;
        try {
            actorRole = UserRole.fromCode(req.actorRole());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown actorRole code: " + req.actorRole());
        }

        Note note = Note.builder()
                .id(idGenerator.next(IdGeneratorService.NOTE, "n-"))
                .learnerJourneyItemId(itemId)
                .message(req.message())
                .actorId(req.actorId())
                .actorName(req.actorName())
                .actorRole(actorRole.getCode())
                .build();

        return toNoteDto(noteRepository.save(note));
    }

    // ─── View builder ────────────────────────────────────────────────────────

    /**
     * Builds the fully composed LearnerJourneyViewDto:
     *   1. Load the Journey template and its items (ordered)
     *   2. For each template item, find the learner's progress row
     *   3. Compute percentComplete and totalTimeSpentHours
     *   4. Attach the journey's exam and this learner's attempt, if any
     */
    public LearnerJourneyViewDto buildView(LearnerJourney lj) {
        Journey journey = journeyService.getEntityById(lj.getJourneyId());
        // DTOs rather than entities: these already carry each item's attachments, which the
        // learner needs in order to see any of the item's content.
        List<JourneyItemDto> templateItems = journeyService.getItemsForJourney(lj.getJourneyId());
        List<LearnerJourneyItem> progressRows =
                learnerJourneyItemRepository.findByLearnerJourneyId(lj.getId());

        List<JourneyItemWithProgressDto> itemViews = templateItems.stream()
                .map(ti -> {
                    LearnerJourneyItem progress = progressRows.stream()
                            .filter(p -> p.getJourneyItemId().equals(ti.id()))
                            .findFirst()
                            .orElse(null);

                    // Fallback: if no progress row exists (shouldn't happen after assign) use a virtual one
                    LearnerJourneyItemDto progressDto = progress != null
                            ? toItemDto(progress)
                            : new LearnerJourneyItemDto(
                            null, lj.getId(), ti.id(),
                            ItemStatus.NEW.toDto(), null, lj.getAssignedAt(), List.of()
                    );

                    return new JourneyItemWithProgressDto(
                            ti.id(), ti.journeyId(), ti.title(),
                            ti.description(), ti.order(), ti.attachments(), progressDto);
                })
                .toList();

        long completedCount = itemViews.stream()
                .filter(LearnerJourneyService::isCompleted)
                .count();
        int percentComplete = itemViews.isEmpty()
                ? 0
                : (int) Math.round((double) completedCount / itemViews.size() * 100);

        // Total hours — sum of completed items only
        double totalHours = itemViews.stream()
                .filter(LearnerJourneyService::isCompleted)
                .mapToDouble(i -> i.progress().timeSpentHours() != null ? i.progress().timeSpentHours() : 0.0)
                .sum();
        totalHours = Math.round(totalHours * 10.0) / 10.0;

        ExamDto exam = examService.getExam(lj.getJourneyId());
        ExamAttemptDto attempt = examService.getAttempt(lj.getId());

        return new LearnerJourneyViewDto(
                lj.getId(), lj.getJourneyId(), lj.getLearnerId(),
                lj.getAssignedById(), lj.getAssignedByName(), lj.getAssignedAt(),
                ItemStatus.fromCode(lj.getStatus()).toDto(), lj.getStartedAt(), lj.getCompletedAt(),
                journeyService.toDto(journey),
                itemViews,
                percentComplete,
                totalHours,
                exam,
                attempt
        );
    }

    /** True when this composed item view is in the COMPLETED state. */
    public static boolean isCompleted(JourneyItemWithProgressDto item) {
        return item.progress() != null
                && item.progress().status() != null
                && item.progress().status().code() == ItemStatus.COMPLETED.getCode();
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
                ItemStatus.fromCode(item.getStatus()).toDto(),
                item.getTimeSpentHours(), item.getUpdatedAt(), notes);
    }

    public NoteDto toNoteDto(Note n) {
        return new NoteDto(n.getId(), n.getActorId(), n.getActorName(),
                UserRole.fromCode(n.getActorRole()).toDto(), n.getMessage(), n.getTimestamp());
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /** Turns an inbound status code into the enum, answering 400 rather than 500 when it's bogus. */
    private ItemStatus resolveStatus(Integer code) {
        try {
            return ItemStatus.fromCode(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status code: " + code);
        }
    }

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
