package com.journey.feature.learnerJourney.service;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.enums.ExamQuestionType;
import com.journey.common.enums.ItemStatus;
import com.journey.common.enums.UserRole;
import com.journey.common.security.AccessPolicy;
import com.journey.common.service.IdGeneratorService;
import com.journey.feature.exam.dto.ExamAttemptDto;
import com.journey.feature.exam.dto.ExamDto;
import com.journey.feature.exam.service.ExamService;
import com.journey.feature.journey.entity.JourneyItem;
import com.journey.feature.journey.entity.JourneyUnit;
import com.journey.feature.journey.entity.UnitQuizQuestion;
import com.journey.feature.journey.service.JourneyService;
import com.journey.feature.learnerJourney.dto.*;
import com.journey.feature.learnerJourney.entity.LearnerJourney;
import com.journey.feature.learnerJourney.entity.LearnerJourneyItem;
import com.journey.feature.learnerJourney.entity.LearnerJourneyUnit;
import com.journey.feature.learnerJourney.entity.Note;
import com.journey.feature.learnerJourney.event.NoteAddedEvent;
import com.journey.feature.learnerJourney.event.UnitStatusChangedEvent;
import com.journey.feature.learnerJourney.repository.LearnerJourneyItemRepository;
import com.journey.feature.learnerJourney.repository.LearnerJourneyRepository;
import com.journey.feature.learnerJourney.repository.LearnerJourneyUnitRepository;
import com.journey.feature.learnerJourney.repository.NoteRepository;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The journey log's unit-based flow: the outline, one item at a time, automatic item progress, unit
 * quizzes, unit review and unit notes.
 *
 * <p><b>Who does what.</b> Items move only through the learner's own actions — opening an item marks it in
 * progress, "Next" completes it, and time is recorded while it is open. Reviewers never touch items; they
 * move units (complete, or send back with a note). Anyone who can see the learner can read everything.
 */
@Service
@RequiredArgsConstructor
public class LearnerUnitService {

    /** Most time one heartbeat may add; the page reports every 30 seconds while the learner is active. */
    static final int MAX_HEARTBEAT_SECONDS = 120;

    private final LearnerJourneyService learnerJourneyService;
    private final LearnerJourneyRepository learnerJourneyRepository;
    private final LearnerJourneyItemRepository itemRepository;
    private final LearnerJourneyUnitRepository unitRepository;
    private final NoteRepository noteRepository;
    private final JourneyService journeyService;
    private final ExamService examService;
    private final UserRepository userRepository;
    private final IdGeneratorService idGenerator;
    private final ApplicationEventPublisher events;
    private final AccessPolicy access;
    private final ObjectMapper mapper;

    // ─── Reading ──────────────────────────────────────────────────────────────

    /** GET /api/learner-journeys/:id/outline */
    @Transactional
    public JourneyOutlineDto outline(String learnerJourneyId) {
        LearnerJourney lj = journey(learnerJourneyId);
        User learner = access.requireViewable(lj.getLearnerId());
        List<LearnerJourneyUnit> unitRows = learnerJourneyService.ensureProgressRows(lj);

        List<JourneyUnit> units = journeyService.getUnitEntitiesForJourney(lj.getJourneyId());
        List<JourneyItem> items = journeyService.getItemEntitiesForJourney(lj.getJourneyId());
        Map<String, LearnerJourneyItem> progressByItem = itemRepository.findByLearnerJourneyId(lj.getId()).stream()
                .collect(Collectors.toMap(LearnerJourneyItem::getJourneyItemId, Function.identity(), (a, b) -> a));
        Map<String, LearnerJourneyUnit> rowByUnit = unitRows.stream()
                .collect(Collectors.toMap(LearnerJourneyUnit::getUnitId, Function.identity(), (a, b) -> a));
        Map<String, Long> itemNoteCounts = noteRepository.findByLearnerJourneyItemIdIn(
                        progressByItem.values().stream().map(LearnerJourneyItem::getId).toList()).stream()
                .collect(Collectors.groupingBy(Note::getLearnerJourneyItemId, Collectors.counting()));
        Map<String, List<Note>> unitNotes = noteRepository.findByLearnerJourneyUnitIdIn(
                        unitRows.stream().map(LearnerJourneyUnit::getId).toList()).stream()
                .collect(Collectors.groupingBy(Note::getLearnerJourneyUnitId));

        List<OutlineUnitDto> unitDtos = new ArrayList<>();
        boolean allReady = true;
        for (JourneyUnit unit : units) {
            LearnerJourneyUnit row = rowByUnit.get(unit.getId());
            List<OutlineItemDto> itemDtos = items.stream()
                    .filter(i -> unit.getId().equals(i.getUnitId()))
                    .map(i -> {
                        LearnerJourneyItem p = progressByItem.get(i.getId());
                        return new OutlineItemDto(p.getId(), i.getId(), i.getTitle(), i.getOrder(),
                                ItemStatus.fromCode(p.getStatus()).toDto(), p.getTimeSpentHours(),
                                itemNoteCounts.getOrDefault(p.getId(), 0L).intValue());
                    })
                    .toList();
            int done = (int) itemDtos.stream().filter(i -> i.status().code() == ItemStatus.COMPLETED.getCode()).count();
            int questionCount = journeyService.getQuizForUnit(unit.getId()).size();
            OutlineQuizDto quiz = questionCount == 0 ? null
                    : new OutlineQuizDto(questionCount, row.getQuizSubmittedAt() != null, row.getQuizScorePercent());
            boolean ready = done == itemDtos.size() && (quiz == null || quiz.answered());
            allReady &= ready;
            unitDtos.add(new OutlineUnitDto(row.getId(), unit.getId(), unit.getTitle(), unit.getDescription(), unit.getOrder(),
                    ItemStatus.fromCode(row.getStatus()).toDto(), done, itemDtos.size(), quiz, ready,
                    unitNotes.getOrDefault(row.getId(), List.of()).stream()
                            .sorted(Comparator.comparing(Note::getTimestamp))
                            .map(learnerJourneyService::toNoteDto).toList(),
                    itemDtos));
        }

        int totalItems = progressByItem.size();
        long completed = progressByItem.values().stream().filter(p -> p.getStatus() == ItemStatus.COMPLETED.getCode()).count();
        double hours = progressByItem.values().stream().mapToDouble(p -> p.getTimeSpentHours() == null ? 0 : p.getTimeSpentHours()).sum();

        ExamDto exam = examService.getExam(lj.getJourneyId());
        ExamAttemptDto attempt = examService.getAttempt(lj.getId());
        OutlineExamDto examDto = exam == null ? null : new OutlineExamDto(exam.id(), exam.title(), allReady,
                attempt == null ? null : attempt.status(), attempt == null ? null : attempt.scorePercent(),
                attempt == null ? null : attempt.passed());

        return new JourneyOutlineDto(lj.getId(), lj.getLearnerId(), learner.getName(),
                journeyService.toDto(journeyService.getEntityById(lj.getJourneyId())),
                ItemStatus.fromCode(lj.getStatus()).toDto(),
                totalItems == 0 ? 0 : (int) Math.round(completed * 100.0 / totalItems),
                Math.round(hours * 10.0) / 10.0,
                lj.getDueDate(), Boolean.TRUE.equals(lj.getSelfEnrolled()), lj.getAssignedById(), lj.getAssignedByName(),
                unitDtos, examDto);
    }

    /** GET /api/learner-journey-items/:id — one item with its content and neighbours. */
    @Transactional(readOnly = true)
    public ItemContentDto item(String progressId) {
        LearnerJourneyItem p = progress(progressId);
        LearnerJourney lj = journey(p.getLearnerJourneyId());
        access.requireViewable(lj.getLearnerId());

        List<JourneyUnit> units = journeyService.getUnitEntitiesForJourney(lj.getJourneyId());
        Map<String, Integer> unitOrder = units.stream().collect(Collectors.toMap(JourneyUnit::getId, JourneyUnit::getOrder));
        List<JourneyItem> ordered = journeyService.getItemEntitiesForJourney(lj.getJourneyId()).stream()
                .sorted(Comparator.comparing((JourneyItem i) -> unitOrder.getOrDefault(i.getUnitId(), 0)).thenComparing(JourneyItem::getOrder))
                .toList();
        Map<String, String> progressIdByItem = itemRepository.findByLearnerJourneyId(lj.getId()).stream()
                .collect(Collectors.toMap(LearnerJourneyItem::getJourneyItemId, LearnerJourneyItem::getId, (a, b) -> a));

        int index = 0;
        for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).getId().equals(p.getJourneyItemId())) index = i;
        JourneyItem current = ordered.get(index);
        JourneyItem previous = index > 0 ? ordered.get(index - 1) : null;
        JourneyItem next = index < ordered.size() - 1 ? ordered.get(index + 1) : null;
        JourneyUnit unit = units.stream().filter(u -> u.getId().equals(current.getUnitId())).findFirst().orElse(null);
        String learnerUnitId = unitRepository.findByLearnerJourneyId(lj.getId()).stream()
                .filter(r -> r.getUnitId().equals(current.getUnitId())).map(LearnerJourneyUnit::getId).findFirst().orElse(null);

        return new ItemContentDto(p.getId(), current.getId(), learnerUnitId, unit == null ? "" : unit.getTitle(),
                current.getTitle(), current.getDescription(), journeyService.toItemDto(current).attachments(),
                ItemStatus.fromCode(p.getStatus()).toDto(), p.getTimeSpentHours(),
                noteRepository.findByLearnerJourneyItemIdOrderByTimestampAsc(p.getId()).stream().map(learnerJourneyService::toNoteDto).toList(),
                previous == null ? null : progressIdByItem.get(previous.getId()),
                next == null ? null : progressIdByItem.get(next.getId()),
                next == null ? null : next.getTitle(),
                next == null || !Objects.equals(next.getUnitId(), current.getUnitId()));
    }

    // ─── The learner's own progress ───────────────────────────────────────────

    /** POST /api/learner-journey-items/:id/open — the learner opened it: new → in progress. */
    @Transactional
    public OutlineItemDto open(String progressId) {
        LearnerJourneyItem p = ownProgress(progressId);
        LearnerJourney lj = journey(p.getLearnerJourneyId());
        if (p.getStatus() == ItemStatus.NEW.getCode()) {
            p.setStatus(ItemStatus.REFLECT.getCode());
            p.setUpdatedAt(LocalDateTime.now());
            itemRepository.save(p);
        }
        String unitId = learnerJourneyService.unitIdOfItem(lj, p.getJourneyItemId());
        if (unitId != null) learnerJourneyService.startUnit(lj, unitId);
        learnerJourneyService.recomputeJourneyStatus(lj.getId());
        return lineOf(p);
    }

    /** POST /api/learner-journey-items/:id/complete — "Next": the learner finished it. */
    @Transactional
    public OutlineItemDto complete(String progressId) {
        LearnerJourneyItem p = ownProgress(progressId);
        LearnerJourney lj = journey(p.getLearnerJourneyId());
        boolean changed = p.getStatus() != ItemStatus.COMPLETED.getCode();
        if (changed) {
            p.setStatus(ItemStatus.COMPLETED.getCode());
            p.setUpdatedAt(LocalDateTime.now());
            itemRepository.save(p);
        }
        String unitId = learnerJourneyService.unitIdOfItem(lj, p.getJourneyItemId());
        if (unitId != null) {
            learnerJourneyService.startUnit(lj, unitId);
            if (changed) learnerJourneyService.submitUnitIfReady(lj, unitId);
        }
        learnerJourneyService.recomputeJourneyStatus(lj.getId());
        return lineOf(p);
    }

    /** POST /api/learner-journey-items/:id/time  { seconds } — active time while the item is open. */
    @Transactional
    public void addTime(String progressId, Integer seconds) {
        if (seconds == null || seconds < 1) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER, "seconds");
        }
        LearnerJourneyItem p = ownProgress(progressId);
        double added = Math.min(seconds, MAX_HEARTBEAT_SECONDS) / 3600.0;
        double total = (p.getTimeSpentHours() == null ? 0 : p.getTimeSpentHours()) + added;
        p.setTimeSpentHours(Math.round(total * 10_000.0) / 10_000.0);
        p.setUpdatedAt(LocalDateTime.now());
        itemRepository.save(p);
    }

    // ─── Quiz ─────────────────────────────────────────────────────────────────

    /** GET /api/learner-journey-units/:id/quiz — answers hidden until the learner has submitted. */
    @Transactional(readOnly = true)
    public UnitQuizDto quiz(String learnerUnitId) {
        LearnerJourneyUnit row = unitRow(learnerUnitId);
        access.requireViewable(journey(row.getLearnerJourneyId()).getLearnerId());
        return quizDto(row);
    }

    /** POST /api/learner-journey-units/:id/quiz — the learner answers; graded on the spot, retakes allowed. */
    @Transactional
    public UnitQuizDto submitQuiz(String learnerUnitId, SubmitQuizRequest req) {
        LearnerJourneyUnit row = unitRow(learnerUnitId);
        LearnerJourney lj = journey(row.getLearnerJourneyId());
        requireLearner(lj);
        if (row.getStatus() == ItemStatus.COMPLETED.getCode() || row.getStatus() == ItemStatus.CANCELLED.getCode()) {
            throw new ApiException(ErrorCode.UNIT_CLOSED);
        }
        List<UnitQuizQuestion> questions = journeyService.getQuizForUnit(row.getUnitId());
        if (questions.isEmpty()) {
            throw new ApiException(ErrorCode.UNIT_NO_QUIZ);
        }
        Map<String, SubmitQuizRequest.Answer> answers = req.answers().stream()
                .collect(Collectors.toMap(SubmitQuizRequest.Answer::questionId, Function.identity(), (a, b) -> b));
        int correct = 0;
        for (UnitQuizQuestion q : questions) {
            SubmitQuizRequest.Answer a = answers.get(q.getId());
            boolean mc = q.getQuestionType() == ExamQuestionType.MULTIPLE_CHOICE.getCode();
            if (a == null || (mc ? a.selectedOptionIndex() == null : a.boolAnswer() == null)) {
                throw new ApiException(ErrorCode.QUIZ_ANSWER_ALL);
            }
            if (isCorrect(q, a)) correct++;
        }
        List<SubmitQuizRequest.Answer> kept = questions.stream().map(q -> answers.get(q.getId())).toList();
        try {
            row.setQuizAnswers(mapper.writeValueAsString(kept));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL);
        }
        row.setQuizScorePercent((int) Math.round(correct * 100.0 / questions.size()));
        row.setQuizSubmittedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        unitRepository.save(row);

        learnerJourneyService.startUnit(lj, row.getUnitId());
        learnerJourneyService.submitUnitIfReady(lj, row.getUnitId());
        learnerJourneyService.recomputeJourneyStatus(lj.getId());
        return quizDto(unitRow(learnerUnitId));
    }

    // ─── Unit review ──────────────────────────────────────────────────────────

    /** POST /api/learner-journey-units/:id/submit — the learner sends a unit (back) for review. */
    @Transactional
    public OutlineUnitSummary submit(String learnerUnitId) {
        LearnerJourneyUnit row = unitRow(learnerUnitId);
        LearnerJourney lj = journey(row.getLearnerJourneyId());
        requireLearner(lj);
        if (!learnerJourneyService.unitReady(lj, row.getUnitId())) {
            throw new ApiException(ErrorCode.UNIT_NOT_READY);
        }
        if (!learnerJourneyService.submitUnitIfReady(lj, row.getUnitId())) {
            throw new ApiException(ErrorCode.UNIT_ALREADY_SUBMITTED);
        }
        return summary(unitRow(learnerUnitId));
    }

    /** PATCH /api/learner-journey-units/:id/status — a reviewer completes the unit or sends it back. */
    @Transactional
    public OutlineUnitSummary updateStatus(String learnerUnitId, Integer statusCode) {
        LearnerJourneyUnit row = unitRow(learnerUnitId);
        LearnerJourney lj = journey(row.getLearnerJourneyId());
        User actor = access.actor();
        User learner = access.requireViewable(lj.getLearnerId());
        if (!access.isReviewerOf(actor, learner)) {
            throw new ApiException(ErrorCode.REVIEWER_ONLY);
        }
        ItemStatus status;
        try {
            status = ItemStatus.fromCode(statusCode);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(ErrorCode.UNKNOWN_CODE, statusCode);
        }
        boolean changed = row.getStatus() != status.getCode();
        row.setStatus(status.getCode());
        row.setUpdatedAt(LocalDateTime.now());
        unitRepository.save(row);
        if (changed) {
            events.publishEvent(new UnitStatusChangedEvent(lj.getId(), journeyTitle(lj), unitTitle(row),
                    status.getEnglish(), status.getArabic(), lj.getLearnerId(), actor.getId()));
        }
        learnerJourneyService.recomputeJourneyStatus(lj.getId());
        return summary(unitRow(learnerUnitId));
    }

    /** POST /api/learner-journey-units/:id/notes — the unit's review thread. */
    @Transactional
    public NoteDto addNote(String learnerUnitId, AddNoteRequest req) {
        LearnerJourneyUnit row = unitRow(learnerUnitId);
        LearnerJourney lj = journey(row.getLearnerJourneyId());
        User actor = access.actor();
        access.requireViewable(lj.getLearnerId());
        UserRole role = AccessPolicy.roleOf(actor);
        Note note = noteRepository.save(Note.builder()
                .id(idGenerator.next(IdGeneratorService.NOTE, "n-"))
                .learnerJourneyUnitId(row.getId())
                .message(req.message())
                .actorId(actor.getId())
                .actorName(actor.getName())
                .actorRole(role.getCode())
                .build());
        events.publishEvent(new NoteAddedEvent(lj.getId(), journeyTitle(lj), unitTitle(row), actor.getId(), actor.getName(),
                role == UserRole.LEARNER, lj.getLearnerId(), lj.getAssignedById()));
        return learnerJourneyService.toNoteDto(note);
    }

    /** A unit's status after a change, for the caller to update its outline without reloading. */
    public record OutlineUnitSummary(String learnerUnitId, com.journey.common.dto.EnumValueDto status, boolean readyForReview) {}

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private OutlineUnitSummary summary(LearnerJourneyUnit row) {
        LearnerJourney lj = journey(row.getLearnerJourneyId());
        return new OutlineUnitSummary(row.getId(), ItemStatus.fromCode(row.getStatus()).toDto(),
                learnerJourneyService.unitReady(lj, row.getUnitId()));
    }

    private UnitQuizDto quizDto(LearnerJourneyUnit row) {
        boolean submitted = row.getQuizSubmittedAt() != null;
        Map<String, SubmitQuizRequest.Answer> saved = submitted ? savedAnswers(row) : Map.of();
        List<QuizQuestionDto> questions = journeyService.getQuizForUnit(row.getUnitId()).stream()
                .map(q -> {
                    SubmitQuizRequest.Answer a = saved.get(q.getId());
                    return new QuizQuestionDto(q.getId(), ExamQuestionType.fromCode(q.getQuestionType()).toDto(), q.getPrompt(),
                            JourneyService.optionsOf(q),
                            a == null ? null : a.selectedOptionIndex(),
                            a == null ? null : a.boolAnswer(),
                            a == null ? null : isCorrect(q, a),
                            submitted ? q.getCorrectOptionIndex() : null,
                            submitted ? q.getCorrectBoolAnswer() : null);
                })
                .toList();
        return new UnitQuizDto(row.getId(), unitTitle(row), questions, submitted, row.getQuizScorePercent(), row.getQuizSubmittedAt());
    }

    private Map<String, SubmitQuizRequest.Answer> savedAnswers(LearnerJourneyUnit row) {
        if (row.getQuizAnswers() == null) return Map.of();
        try {
            List<SubmitQuizRequest.Answer> list = mapper.readValue(row.getQuizAnswers(), new TypeReference<List<SubmitQuizRequest.Answer>>() {});
            return list.stream().filter(Objects::nonNull)
                    .collect(Collectors.toMap(SubmitQuizRequest.Answer::questionId, Function.identity(), (a, b) -> b));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static boolean isCorrect(UnitQuizQuestion q, SubmitQuizRequest.Answer a) {
        return q.getQuestionType() == ExamQuestionType.MULTIPLE_CHOICE.getCode()
                ? Objects.equals(q.getCorrectOptionIndex(), a.selectedOptionIndex())
                : Objects.equals(q.getCorrectBoolAnswer(), a.boolAnswer());
    }

    private OutlineItemDto lineOf(LearnerJourneyItem p) {
        JourneyItem item = journeyService.getItemEntitiesForJourney(journey(p.getLearnerJourneyId()).getJourneyId()).stream()
                .filter(i -> i.getId().equals(p.getJourneyItemId())).findFirst().orElse(null);
        return new OutlineItemDto(p.getId(), p.getJourneyItemId(), item == null ? "" : item.getTitle(),
                item == null ? 0 : item.getOrder(), ItemStatus.fromCode(p.getStatus()).toDto(), p.getTimeSpentHours(), 0);
    }

    /** The caller's own item progress; nobody else moves a learner's items. */
    private LearnerJourneyItem ownProgress(String progressId) {
        LearnerJourneyItem p = progress(progressId);
        requireLearner(journey(p.getLearnerJourneyId()));
        return p;
    }

    private void requireLearner(LearnerJourney lj) {
        if (!lj.getLearnerId().equals(access.actor().getId())) {
            throw new ApiException(ErrorCode.LEARNER_ONLY);
        }
        if (lj.getStatus() == ItemStatus.CANCELLED.getCode()) {
            throw new ApiException(ErrorCode.JOURNEY_CANCELLED);
        }
    }

    private String journeyTitle(LearnerJourney lj) {
        return journeyService.getEntityById(lj.getJourneyId()).getTitle();
    }

    private String unitTitle(LearnerJourneyUnit row) {
        return journeyService.getUnitEntitiesForJourney(journey(row.getLearnerJourneyId()).getJourneyId()).stream()
                .filter(u -> u.getId().equals(row.getUnitId())).map(JourneyUnit::getTitle).findFirst().orElse("");
    }

    private LearnerJourney journey(String id) {
        return learnerJourneyRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));
    }

    private LearnerJourneyItem progress(String id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ITEM_PROGRESS_NOT_FOUND));
    }

    private LearnerJourneyUnit unitRow(String id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.UNIT_PROGRESS_NOT_FOUND));
    }
}
