package com.journey.feature.learnerJourney.service;

import com.journey.common.enums.ItemStatus;
import com.journey.common.enums.UserRole;
import com.journey.common.security.AccessPolicy;
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
import com.journey.feature.learnerJourney.event.ItemStatusChangedEvent;
import com.journey.feature.learnerJourney.event.JourneyAssignedEvent;
import com.journey.feature.learnerJourney.event.JourneyCancelledEvent;
import com.journey.feature.learnerJourney.event.JourneyCompletedEvent;
import com.journey.feature.learnerJourney.event.NoteAddedEvent;
import com.journey.feature.learnerJourney.repository.LearnerJourneyItemRepository;
import com.journey.feature.learnerJourney.repository.LearnerJourneyRepository;
import com.journey.feature.learnerJourney.repository.NoteRepository;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LearnerJourneyService {

    private final LearnerJourneyRepository learnerJourneyRepository;
    private final LearnerJourneyItemRepository learnerJourneyItemRepository;
    private final NoteRepository noteRepository;
    private final JourneyService journeyService;
    private final ExamService examService;
    private final IdGeneratorService idGenerator;
    private final ApplicationEventPublisher events;
    private final AccessPolicy access;
    private final UserRepository userRepository;

    // ─── Queries ──────────────────────────────────────────────────────────────
    //
    // Access: whoever can see the learner (themselves, their senior, their department's manager,
    // HR, Admin) can see their journeys. Mutations narrow that further — see each method.
    // Who performed an action is always the signed-in caller, never a value from the request body.

    /** GET /api/learner-journeys?learnerId=x */
    public List<LearnerJourneyViewDto> getLearnerJourneyViews(String learnerId) {
        access.requireViewable(learnerId);
        return learnerJourneyRepository.findByLearnerId(learnerId).stream()
                .map(this::buildView)
                .toList();
    }

    /** GET /api/learner-journeys/:id — the fully composed quest-log view. */
    public LearnerJourneyViewDto getLearnerJourneyView(String id) {
        LearnerJourney lj = findLjOrThrow(id);
        access.requireViewable(lj.getLearnerId());
        return buildView(lj);
    }

    // ─── Mutations ────────────────────────────────────────────────────────────

    /**
     * POST /api/learner-journeys
     * Assign a journey template to a learner and auto-create a LearnerJourneyItem row for each item.
     *
     * <p>Staff only, and only to a learner the caller can see: a Senior their own learners, a Manager
     * their department's, HR and Admin anyone's.
     */
    @Transactional
    public LearnerJourneyViewDto assignJourney(AssignJourneyRequest req) {
        User actor = access.requireRole(AccessPolicy.STAFF);
        User learner = access.requireViewable(req.learnerId());
        if (!AccessPolicy.hasRole(learner, UserRole.LEARNER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    learner.getName() + " is not a Learner.");
        }
        journeyService.getEntityById(req.journeyId()); // 404 for an unknown journey, before any writes

        // A cancelled assignment doesn't count: the journey can be given again, starting fresh.
        if (findActiveAssignment(req.journeyId(), req.learnerId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This learner is already assigned to that journey.");
        }

        LearnerJourney saved = createAssignment(req.journeyId(), learner, actor);

        /*
          Announced through an event rather than by calling the notifier directly, so this service
          keeps knowing nothing about notifications. The listener runs after this transaction
          commits — a rollback here must not leave the learner told about an assignment that no
          longer exists.
         */
        events.publishEvent(new JourneyAssignedEvent(
                saved.getId(),
                saved.getLearnerId(),
                saved.getAssignedById(),
                saved.getAssignedByName(),
                journeyService.getEntityById(req.journeyId()).getTitle()));

        return buildView(saved);
    }

    /**
     * The learner's live (not cancelled) assignment of a journey, if any.
     */
    public Optional<LearnerJourney> findActiveAssignment(String journeyId, String learnerId) {
        return learnerJourneyRepository.findFirstByJourneyIdAndLearnerIdAndStatusNot(
                journeyId, learnerId, ItemStatus.CANCELLED.getCode());
    }

    /**
     * Creates the assignment and one NEW progress row per template item. No access checks and no
     * event: callers (a single assignment above, or a package) have already checked, and announce
     * it their own way — a package sends one notification, not one per journey.
     */
    @Transactional
    public LearnerJourney createAssignment(String journeyId, User learner, User actor) {
        return createAssignment(journeyId, learner, actor, false);
    }

    /**
     * @param assigner     who is recorded as assigning it; for a self-enrollment, the reviewer
     * @param selfEnrolled the learner enrolled from the catalog
     */
    @Transactional
    public LearnerJourney createAssignment(String journeyId, User learner, User assigner, boolean selfEnrolled) {
        LearnerJourney saved = learnerJourneyRepository.save(LearnerJourney.builder()
                .id(idGenerator.next(IdGeneratorService.LEARNER_JOURNEY, "lj-"))
                .journeyId(journeyId)
                .learnerId(learner.getId())
                .assignedById(assigner.getId())
                .assignedByName(assigner.getName())
                .selfEnrolled(selfEnrolled)
                .status(ItemStatus.NEW.getCode())
                .build());

        journeyService.getItemEntitiesForJourney(journeyId).forEach(item ->
                learnerJourneyItemRepository.save(LearnerJourneyItem.builder()
                        .id(idGenerator.next(IdGeneratorService.LEARNER_JOURNEY_ITEM, "lji-"))
                        .learnerJourneyId(saved.getId())
                        .journeyItemId(item.getId())
                        .status(ItemStatus.NEW.getCode())
                        .build()));
        return saved;
    }

    /** Cancels without an event, for callers that announce the cancellation themselves. */
    @Transactional
    public void cancelQuietly(String learnerJourneyId) {
        LearnerJourney lj = findLjOrThrow(learnerJourneyId);
        lj.setStatus(ItemStatus.CANCELLED.getCode());
        learnerJourneyRepository.save(lj);
    }

    /** Share of completed items, 0–100 — without composing the whole view. */
    public int percentComplete(String learnerJourneyId) {
        List<LearnerJourneyItem> items = learnerJourneyItemRepository.findByLearnerJourneyId(learnerJourneyId);
        if (items.isEmpty()) return 0;
        long done = items.stream().filter(i -> i.getStatus() == ItemStatus.COMPLETED.getCode()).count();
        return (int) Math.round((double) done / items.size() * 100);
    }

    public LearnerJourney getEntity(String learnerJourneyId) {
        return findLjOrThrow(learnerJourneyId);
    }

    /**
     * PATCH /api/learner-journeys/:id/status — overriding a whole journey's status (cancelling it,
     * forcing it complete) is a management decision: Manager, HR or Admin, within their reach.
     */
    @Transactional
    public LearnerJourneyViewDto updateJourneyStatus(String id, UpdateJourneyStatusRequest req) {
        access.requireRole(UserRole.MANAGER, UserRole.HR, UserRole.ADMIN);
        LearnerJourney lj = findLjOrThrow(id);
        access.requireViewable(lj.getLearnerId());
        ItemStatus status = resolveStatus(req.status());
        boolean wasCancelled = lj.getStatus() == ItemStatus.CANCELLED.getCode();

        lj.setStatus(status.getCode());
        if (status == ItemStatus.COMPLETED && lj.getCompletedAt() == null) {
            lj.setCompletedAt(LocalDateTime.now());
        }
        learnerJourneyRepository.save(lj);

        // Only on the transition — re-cancelling an already cancelled journey is not news.
        if (status == ItemStatus.CANCELLED && !wasCancelled) {
            events.publishEvent(new JourneyCancelledEvent(
                    lj.getId(), journeyTitleOf(lj), lj.getLearnerId()));
        }

        return buildView(lj);
    }

    /**
     * PATCH /api/learner-journey-items/:itemId/status — the learner working through their own journey,
     * or a reviewer who can see them moving it on their behalf.
     */
    @Transactional
    public LearnerJourneyItemDto updateItemStatus(String itemId, UpdateItemStatusRequest req) {
        LearnerJourneyItem item = findLjiOrThrow(itemId);
        User actor = access.actor();
        User learner = learnerOf(item);
        if (!actor.getId().equals(learner.getId()) && !access.isReviewerOf(actor, learner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot change this learner's progress.");
        }
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

        /*
          The listener drops this when the learner moved their own item, but the check needs the
          learner id, which only this side knows. Both labels of the status travel with the event:
          the notification is rendered in the reader.s language, so picking one here would come out
          wrong in the other.
         */
        LearnerJourney parent = findLjOrThrow(saved.getLearnerJourneyId());
        events.publishEvent(new ItemStatusChangedEvent(
                parent.getId(),
                journeyTitleOf(parent),
                itemTitleOf(parent, saved.getJourneyItemId()),
                status.getEnglish(),
                status.getArabic(),
                parent.getLearnerId(),
                actor.getId()));

        return toItemDto(saved);
    }

    /**
     * POST /api/learner-journey-items/:itemId/notes — anyone who can see the learner can join the
     * conversation. The author is the signed-in caller; the request body's actor fields are ignored,
     * otherwise anyone could post a note in someone else's name.
     */
    @Transactional
    public NoteDto addNote(String itemId, AddNoteRequest req) {
        LearnerJourneyItem lji = findLjiOrThrow(itemId);
        User actor = access.actor();
        access.requireViewable(learnerOf(lji).getId());
        UserRole actorRole = AccessPolicy.roleOf(actor);

        Note note = Note.builder()
                .id(idGenerator.next(IdGeneratorService.NOTE, "n-"))
                .learnerJourneyItemId(itemId)
                .message(req.message())
                .actorId(actor.getId())
                .actorName(actor.getName())
                .actorRole(actorRole.getCode())
                .build();

        NoteDto saved = toNoteDto(noteRepository.save(note));

        LearnerJourney parent = findLjOrThrow(lji.getLearnerJourneyId());
        events.publishEvent(new NoteAddedEvent(
                parent.getId(),
                journeyTitleOf(parent),
                itemTitleOf(parent, lji.getJourneyItemId()),
                actor.getId(),
                actor.getName(),
                actorRole == UserRole.LEARNER,
                parent.getLearnerId(),
                parent.getAssignedById()));

        return saved;
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
                Boolean.TRUE.equals(lj.getSelfEnrolled()),
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
        boolean wasCompleted = lj.getStatus() == ItemStatus.COMPLETED.getCode();

        if (allCompleted) {
            lj.setStatus(ItemStatus.COMPLETED.getCode());
            if (lj.getCompletedAt() == null) lj.setCompletedAt(LocalDateTime.now());
        } else if (anyStarted && lj.getStatus() == ItemStatus.NEW.getCode()) {
            lj.setStatus(ItemStatus.REFLECT.getCode());
            if (lj.getStartedAt() == null) lj.setStartedAt(LocalDateTime.now());
        }
        learnerJourneyRepository.save(lj);

        /*
          Fires on the transition only. This method runs after every single item update, so
          announcing whenever allCompleted holds would re-notify on each later edit of an already
          finished journey.
         */
        if (allCompleted && !wasCompleted) {
            events.publishEvent(new JourneyCompletedEvent(
                    lj.getId(), journeyTitleOf(lj), lj.getLearnerId(), lj.getAssignedById()));
        }
    }

    /** Title of the journey template behind a learner journey. */
    private String journeyTitleOf(LearnerJourney lj) {
        return journeyService.getEntityById(lj.getJourneyId()).getTitle();
    }

    /** Title of one template item, or empty if it has since been removed from the journey. */
    private String itemTitleOf(LearnerJourney lj, String journeyItemId) {
        return journeyService.getItemEntitiesForJourney(lj.getJourneyId()).stream()
                .filter(item -> item.getId().equals(journeyItemId))
                .findFirst()
                .map(JourneyItem::getTitle)
                .orElse("");
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

    /** The learner whose journey this item belongs to. */
    private User learnerOf(LearnerJourneyItem item) {
        String learnerId = findLjOrThrow(item.getLearnerJourneyId()).getLearnerId();
        return userRepository.findById(learnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Learner not found."));
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
