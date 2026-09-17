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
import com.journey.feature.journey.entity.JourneyUnit;
import com.journey.feature.journey.entity.UnitQuizQuestion;
import com.journey.feature.journey.dto.JourneyUnitDto;
import com.journey.feature.journey.repository.JourneyUnitRepository;
import com.journey.feature.journey.repository.UnitQuizQuestionRepository;
import com.journey.feature.exam.dto.ExamQuestionDto;
import com.journey.feature.exam.dto.QuestionDraftDto;
import com.journey.feature.exam.service.ExamService;
import com.journey.common.enums.ExamQuestionType;
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
    private final JourneyUnitRepository unitRepository;
    private final UnitQuizQuestionRepository quizRepository;

    /** Unit quizzes are meant to be short ("1–2 questions"); this is the hard ceiling. */
    static final int MAX_QUIZ_QUESTIONS = 5;

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

    public List<JourneyUnit> getUnitEntitiesForJourney(String journeyId) {
        return unitRepository.findByJourneyIdOrderByOrder(journeyId);
    }

    public List<UnitQuizQuestion> getQuizForUnit(String unitId) {
        return quizRepository.findByUnitIdOrderByQuestionOrder(unitId);
    }

    /**
     * GET /api/journeys/:id/units — the full structure for editing, quiz answers included. Staff only;
     * learners get their quiz without answers through the learner-journey endpoints.
     */
    public List<JourneyUnitDto> getUnitsForJourney(String journeyId) {
        access.requireRole(AccessPolicy.STAFF);
        findOrThrow(journeyId);
        List<JourneyItemDto> items = getItemsForJourney(journeyId);
        return unitRepository.findByJourneyIdOrderByOrder(journeyId).stream()
                .map(u -> new JourneyUnitDto(u.getId(), u.getTitle(), u.getDescription(), u.getOrder(),
                        items.stream().filter(i -> u.getId().equals(i.unitId())).toList(),
                        quizRepository.findByUnitIdOrderByQuestionOrder(u.getId()).stream().map(this::toQuizDto).toList()))
                .toList();
    }

    public ExamQuestionDto toQuizDto(UnitQuizQuestion q) {
        return new ExamQuestionDto(q.getId(), ExamQuestionType.fromCode(q.getQuestionType()).toDto(), q.getPrompt(),
                optionsOf(q), q.getCorrectOptionIndex(), q.getCorrectBoolAnswer());
    }

    public static List<String> optionsOf(UnitQuizQuestion q) {
        return Stream.of(q.getOption1(), q.getOption2(), q.getOption3(), q.getOption4()).filter(Objects::nonNull).toList();
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
                .targetDays(req.targetDays())
                .createdById(author.getId())
                .createdByName(author.getName())
                .build();
        Journey saved = journeyRepository.save(journey);
        saveStructure(saved.getId(), unitsOf(req, List.of()));
        return toDto(saved);
    }

    @Transactional
    public JourneyDto updateJourney(String id, CreateJourneyRequest req) {
        access.requireRole(AccessPolicy.STAFF);
        Journey journey = findOrThrow(id);
        journey.setTitle(req.title());
        journey.setDescription(req.description());
        journey.setTechTag(req.techTag());
        journey.setTargetDays(req.targetDays());
        journey.setUpdatedAt(LocalDateTime.now());
        Journey saved = journeyRepository.save(journey);

        saveStructure(id, unitsOf(req, unitRepository.findByJourneyIdOrderByOrder(id)));
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

    /**
     * The request's units, or — for a client still sending a flat item list — one unit holding them,
     * reusing the journey's first unit so its learners' progress is kept.
     */
    private List<CreateJourneyRequest.UnitPayload> unitsOf(CreateJourneyRequest req, List<JourneyUnit> existing) {
        if (req.units() != null && !req.units().isEmpty()) return req.units();
        if (req.items() == null || req.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A journey needs at least one item.");
        }
        JourneyUnit first = existing.isEmpty() ? null : existing.get(0);
        return List.of(new CreateJourneyRequest.UnitPayload(
                first == null ? null : first.getId(),
                first == null ? "Unit 1" : first.getTitle(),
                first == null ? null : first.getDescription(),
                req.items(),
                first == null ? null : quizDrafts(first.getId())));
    }

    /**
     * Saves units, items, attachments and quizzes <b>in place</b>: an existing unit or item keeps its id
     * and row, so learners' progress and notes on it survive the edit. Only units and items the author
     * removed are deleted (and their progress with them).
     *
     * <p>This used to delete every item and re-insert it with the same id. The database cascades item
     * deletes to learner progress and notes, so saving a journey — even unchanged — erased every
     * learner's progress on it.
     *
     * <p>Only the files the author actually dropped are deleted from storage; an edit re-sends the
     * attachments it keeps, storage key and all.
     */
    private void saveStructure(String journeyId, List<CreateJourneyRequest.UnitPayload> units) {
        if (units.stream().allMatch(u -> u.items() == null || u.items().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A journey needs at least one item.");
        }
        Map<String, JourneyUnit> existingUnits = unitRepository.findByJourneyIdOrderByOrder(journeyId).stream()
                .collect(Collectors.toMap(JourneyUnit::getId, u -> u));
        Map<String, JourneyItem> existingItems = getItemEntitiesForJourney(journeyId).stream()
                .collect(Collectors.toMap(JourneyItem::getId, i -> i));

        // Storage: drop bytes no longer referenced anywhere in the journey. Rows are rewritten below.
        Set<String> retained = units.stream()
                .flatMap(u -> u.items() == null ? Stream.empty() : u.items().stream())
                .flatMap(i -> i.attachments() == null ? Stream.<CreateJourneyRequest.AttachmentPayload>empty() : i.attachments().stream())
                .map(CreateJourneyRequest.AttachmentPayload::storageKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        clearAttachmentsFor(List.copyOf(existingItems.values()), retained);

        Set<String> keptUnits = new java.util.HashSet<>();
        Set<String> keptItems = new java.util.HashSet<>();
        int itemOrder = 1;
        for (int u = 0; u < units.size(); u++) {
            CreateJourneyRequest.UnitPayload payload = units.get(u);
            if (payload.items() == null || payload.items().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unit \"" + payload.title() + "\" needs at least one item.");
            }
            JourneyUnit unit = payload.id() != null && existingUnits.containsKey(payload.id())
                    ? existingUnits.get(payload.id())
                    : JourneyUnit.builder().id(idGenerator.next(IdGeneratorService.JOURNEY_UNIT, "ju-")).journeyId(journeyId).build();
            unit.setTitle(payload.title().trim());
            unit.setDescription(payload.description());
            unit.setOrder(u + 1);
            unitRepository.save(unit);
            keptUnits.add(unit.getId());

            for (CreateJourneyRequest.ItemPayload itemPayload : payload.items()) {
                // Only reuse an id that already belongs to this journey; anything else is a new item.
                JourneyItem item = itemPayload.id() != null && existingItems.containsKey(itemPayload.id())
                        ? existingItems.get(itemPayload.id())
                        : JourneyItem.builder().id(idGenerator.next(IdGeneratorService.JOURNEY_ITEM, "ji-")).journeyId(journeyId).build();
                item.setUnitId(unit.getId());
                item.setTitle(itemPayload.title());
                item.setDescription(itemPayload.description());
                item.setOrder(itemOrder++);
                journeyItemRepository.save(item);
                keptItems.add(item.getId());
                saveAttachments(item.getId(), itemPayload.attachments());
            }
            saveQuiz(unit.getId(), payload.quiz());
        }
        journeyItemRepository.flush();

        existingItems.keySet().stream().filter(id -> !keptItems.contains(id))
                .forEach(journeyItemRepository::deleteById);
        existingUnits.keySet().stream().filter(id -> !keptUnits.contains(id))
                .forEach(unitRepository::deleteById);
    }

    /** Up to five auto-graded questions; existing ids are kept so learners' saved answers still match. */
    private void saveQuiz(String unitId, List<QuestionDraftDto> drafts) {
        List<QuestionDraftDto> questions = drafts == null ? List.of() : drafts;
        if (questions.size() > MAX_QUIZ_QUESTIONS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A unit quiz can have at most " + MAX_QUIZ_QUESTIONS + " questions.");
        }
        Map<String, UnitQuizQuestion> existing = quizRepository.findByUnitIdOrderByQuestionOrder(unitId).stream()
                .collect(Collectors.toMap(UnitQuizQuestion::getId, q -> q));
        Set<String> kept = new java.util.HashSet<>();
        for (int i = 0; i < questions.size(); i++) {
            QuestionDraftDto d = questions.get(i);
            ExamQuestionType type;
            try {
                type = ExamQuestionType.fromCode(d.type());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown question type: " + d.type());
            }
            if (type == ExamQuestionType.OPEN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unit quizzes grade themselves, so they take multiple-choice or yes/no questions only.");
            }
            if (d.prompt() == null || d.prompt().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every quiz question needs a prompt.");
            }
            List<String> options = d.options() == null ? List.of() : d.options().stream().filter(o -> o != null && !o.isBlank()).toList();
            if (type == ExamQuestionType.MULTIPLE_CHOICE) {
                if (options.size() < 2 || options.size() > 4) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A multiple-choice question needs 2 to 4 options.");
                }
                Integer correct = d.correctOptionIndex();
                if (correct == null || correct < ExamService.OPTION_CODE_BASE || correct >= ExamService.OPTION_CODE_BASE + options.size()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick the correct option for \"" + d.prompt() + "\".");
                }
            } else if (d.correctBoolAnswer() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick yes or no as the answer for \"" + d.prompt() + "\".");
            }

            UnitQuizQuestion q = d.id() != null && existing.containsKey(d.id())
                    ? existing.get(d.id())
                    : UnitQuizQuestion.builder().id(idGenerator.next(IdGeneratorService.UNIT_QUIZ_QUESTION, "uq-")).unitId(unitId).build();
            q.setQuestionOrder(i + 1);
            q.setQuestionType(type.getCode());
            q.setPrompt(d.prompt().trim());
            boolean mc = type == ExamQuestionType.MULTIPLE_CHOICE;
            q.setOption1(mc && options.size() > 0 ? options.get(0) : null);
            q.setOption2(mc && options.size() > 1 ? options.get(1) : null);
            q.setOption3(mc && options.size() > 2 ? options.get(2) : null);
            q.setOption4(mc && options.size() > 3 ? options.get(3) : null);
            q.setCorrectOptionIndex(mc ? d.correctOptionIndex() : null);
            q.setCorrectBoolAnswer(mc ? null : d.correctBoolAnswer());
            quizRepository.save(q);
            kept.add(q.getId());
        }
        existing.keySet().stream().filter(id -> !kept.contains(id)).forEach(quizRepository::deleteById);
    }

    private List<QuestionDraftDto> quizDrafts(String unitId) {
        return quizRepository.findByUnitIdOrderByQuestionOrder(unitId).stream()
                .map(q -> new QuestionDraftDto(q.getId(), q.getQuestionType(), q.getPrompt(), optionsOf(q),
                        q.getCorrectOptionIndex(), q.getCorrectBoolAnswer()))
                .toList();
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
        return new JourneyDto(j.getId(), j.getTitle(), j.getDescription(), j.getTechTag(), j.getTargetDays(),
                j.getCreatedById(), j.getCreatedByName(), j.getCreatedAt(), j.getUpdatedAt());
    }

    public JourneyItemDto toItemDto(JourneyItem i) {
        return toItemDto(i, attachmentRepository.findByJourneyItemIdOrderByOrder(i.getId()));
    }

    private JourneyItemDto toItemDto(JourneyItem i, List<JourneyItemAttachment> attachments) {
        return new JourneyItemDto(
                i.getId(), i.getJourneyId(), i.getUnitId(), i.getTitle(), i.getDescription(), i.getOrder(),
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
