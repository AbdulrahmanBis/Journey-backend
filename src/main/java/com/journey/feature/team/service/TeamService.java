package com.journey.feature.team.service;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.enums.AttentionType;
import com.journey.common.enums.ExamAttemptStatus;
import com.journey.common.enums.ItemStatus;
import com.journey.common.enums.LearnerHealth;
import com.journey.common.enums.UserRole;
import com.journey.common.security.AccessPolicy;
import com.journey.feature.department.dto.DepartmentDto;
import com.journey.feature.department.entity.Department;
import com.journey.feature.department.repository.DepartmentRepository;
import com.journey.feature.exam.entity.ExamAttempt;
import com.journey.feature.exam.repository.ExamAttemptRepository;
import com.journey.feature.journey.entity.Journey;
import com.journey.feature.journey.entity.JourneyItem;
import com.journey.feature.journey.repository.JourneyItemRepository;
import com.journey.feature.journey.repository.JourneyRepository;
import com.journey.feature.learnerJourney.entity.LearnerJourney;
import com.journey.feature.learnerJourney.entity.LearnerJourneyItem;
import com.journey.feature.learnerJourney.entity.Note;
import com.journey.feature.learnerJourney.repository.LearnerJourneyItemRepository;
import com.journey.feature.learnerJourney.repository.LearnerJourneyRepository;
import com.journey.feature.learnerJourney.repository.NoteRepository;
import com.journey.feature.team.dto.AttentionItemDto;
import com.journey.feature.team.dto.CurrentJourneyDto;
import com.journey.feature.team.dto.LearnerSnapshotDto;
import com.journey.feature.team.dto.TeamKpisDto;
import com.journey.feature.team.dto.TeamLearnerDto;
import com.journey.feature.team.dto.TeamOverviewDto;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The team dashboard: every learner a staff member looks after as one flat list, plus what needs
 * their attention. Replaces drilling senior → learner → journey.
 *
 * <p>Scope follows AccessPolicy: a Senior sees their own learners; a Manager their department; HR and
 * Admin everyone, optionally narrowed to a department. A senior filter narrows further.
 *
 * <p>Data is read in bulk (a handful of queries for the whole team, not per learner) and combined in
 * memory, which is comfortable for teams of hundreds.
 *
 * <p>Review happens on units: "waiting for review" and unanswered review notes come from the learner's units;
 * questions on individual items still count as unanswered notes.
 *
 * <p><b>Signals.</b> Overdue: an open journey past its due date. At risk: no activity for
 * {@value #INACTIVE_DAYS} days, or a due date within {@value #DUE_SOON_DAYS} days with less than
 * {@value #DUE_SOON_MIN_PROGRESS}% done. Activity is any item status change, note, or exam submission.
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    static final int INACTIVE_DAYS = 7;
    static final int DUE_SOON_DAYS = 3;
    static final int DUE_SOON_MIN_PROGRESS = 80;

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LearnerJourneyRepository learnerJourneyRepository;
    private final LearnerJourneyItemRepository itemRepository;
    private final NoteRepository noteRepository;
    private final ExamAttemptRepository attemptRepository;
    private final JourneyRepository journeyRepository;
    private final JourneyItemRepository journeyItemRepository;
    private final com.journey.feature.learnerJourney.repository.LearnerJourneyUnitRepository unitProgressRepository;
    private final com.journey.feature.journey.repository.JourneyUnitRepository unitRepository;
    private final AccessPolicy access;

    // ─── Endpoints ────────────────────────────────────────────────────────────

    public TeamOverviewDto overview(String departmentId, String seniorId) {
        User actor = access.requireRole(AccessPolicy.STAFF);
        List<User> learners;
        List<User> seniorsInScope;
        if (AccessPolicy.hasRole(actor, UserRole.SENIOR)) {
            learners = userRepository.findByRoleAndSeniorId(UserRole.LEARNER.getCode(), actor.getId());
            seniorsInScope = List.of();
        } else {
            String scope = access.departmentScope(departmentId);
            learners = scope == null
                    ? userRepository.findByRole(UserRole.LEARNER.getCode())
                    : userRepository.findByRoleAndDepartmentId(UserRole.LEARNER.getCode(), scope);
            seniorsInScope = scope == null
                    ? userRepository.findByRole(UserRole.SENIOR.getCode())
                    : userRepository.findByRoleAndDepartmentId(UserRole.SENIOR.getCode(), scope);
            if (seniorId != null && !seniorId.isBlank()) {
                learners = learners.stream().filter(l -> seniorId.equals(l.getSeniorId())).toList();
            }
        }
        boolean flagMissingSenior = !AccessPolicy.hasRole(actor, UserRole.SENIOR);

        Snapshot snapshot = load(learners);
        List<TeamLearnerDto> rows = new ArrayList<>();
        List<AttentionItemDto> attention = new ArrayList<>();
        for (User learner : learners) {
            List<AttentionItemDto> own = attentionFor(learner, snapshot, flagMissingSenior);
            attention.addAll(own);
            rows.add(row(learner, snapshot, own.size()));
        }
        attention.sort(ATTENTION_ORDER);
        rows.sort(Comparator.comparingInt((TeamLearnerDto r) -> r.health().code()).thenComparing(TeamLearnerDto::name, String.CASE_INSENSITIVE_ORDER));

        Map<String, Long> perSenior = learners.stream()
                .filter(l -> l.getSeniorId() != null)
                .collect(Collectors.groupingBy(User::getSeniorId, Collectors.counting()));
        List<TeamOverviewDto.SeniorOptionDto> seniors = seniorsInScope.stream()
                .sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
                .map(s -> new TeamOverviewDto.SeniorOptionDto(s.getId(), s.getName(), perSenior.getOrDefault(s.getId(), 0L).intValue()))
                .toList();

        return new TeamOverviewDto(kpis(rows, attention, snapshot), attention, rows, seniors);
    }

    /** One learner's row and attention items; anyone who can see the learner, staff or themselves. */
    public LearnerSnapshotDto learner(String learnerId) {
        User actor = access.actor();
        User learner = access.requireViewable(learnerId);
        if (!AccessPolicy.hasRole(learner, UserRole.LEARNER)) {
            throw new ApiException(ErrorCode.NOT_A_LEARNER, learner.getName());
        }
        Snapshot snapshot = load(List.of(learner));
        List<AttentionItemDto> attention = attentionFor(learner, snapshot, !AccessPolicy.hasRole(actor, UserRole.SENIOR, UserRole.LEARNER));
        attention.sort(ATTENTION_ORDER);
        return new LearnerSnapshotDto(row(learner, snapshot, attention.size()), attention);
    }

    // ─── Loading ──────────────────────────────────────────────────────────────

    /** Everything about a set of learners, read in bulk. */
    private record Snapshot(
            Map<String, List<LearnerJourney>> journeysByLearner,
            Map<String, List<LearnerJourneyItem>> itemsByJourney,
            Map<String, List<Note>> notesByItem,
            Map<String, ExamAttempt> attemptByJourney,
            Map<String, Journey> journeys,
            Map<String, JourneyItem> journeyItems,
            Map<String, List<com.journey.feature.learnerJourney.entity.LearnerJourneyUnit>> unitsByJourney,
            Map<String, List<Note>> notesByUnit,
            Map<String, String> unitTitles,
            Map<String, User> seniors,
            Map<String, Department> departments
    ) {}

    private Snapshot load(List<User> learners) {
        List<String> learnerIds = learners.stream().map(User::getId).toList();
        List<LearnerJourney> ljs = learnerIds.isEmpty() ? List.of() : learnerJourneyRepository.findByLearnerIdIn(learnerIds);
        List<String> ljIds = ljs.stream().map(LearnerJourney::getId).toList();
        List<LearnerJourneyItem> items = ljIds.isEmpty() ? List.of() : itemRepository.findByLearnerJourneyIdIn(ljIds);
        List<String> itemIds = items.stream().map(LearnerJourneyItem::getId).toList();
        List<Note> notes = itemIds.isEmpty() ? List.of() : noteRepository.findByLearnerJourneyItemIdIn(itemIds);
        List<ExamAttempt> attempts = ljIds.isEmpty() ? List.of() : attemptRepository.findByLearnerJourneyIdIn(ljIds);
        List<com.journey.feature.learnerJourney.entity.LearnerJourneyUnit> unitRows = ljIds.isEmpty() ? List.of()
                : unitProgressRepository.findByLearnerJourneyIdIn(ljIds);
        List<Note> unitNotes = unitRows.isEmpty() ? List.of()
                : noteRepository.findByLearnerJourneyUnitIdIn(unitRows.stream().map(com.journey.feature.learnerJourney.entity.LearnerJourneyUnit::getId).toList());

        Set<String> journeyIds = ljs.stream().map(LearnerJourney::getJourneyId).collect(Collectors.toSet());
        Set<String> journeyItemIds = items.stream().map(LearnerJourneyItem::getJourneyItemId).collect(Collectors.toSet());
        Set<String> seniorIds = learners.stream().map(User::getSeniorId).filter(Objects::nonNull).collect(Collectors.toSet());

        return new Snapshot(
                ljs.stream().collect(Collectors.groupingBy(LearnerJourney::getLearnerId)),
                items.stream().collect(Collectors.groupingBy(LearnerJourneyItem::getLearnerJourneyId)),
                notes.stream().collect(Collectors.groupingBy(Note::getLearnerJourneyItemId)),
                attempts.stream().collect(Collectors.toMap(ExamAttempt::getLearnerJourneyId, Function.identity(), (a, b) -> a)),
                byId(journeyRepository.findAllById(journeyIds), Journey::getId),
                byId(journeyItemRepository.findAllById(journeyItemIds), JourneyItem::getId),
                unitRows.stream().collect(Collectors.groupingBy(com.journey.feature.learnerJourney.entity.LearnerJourneyUnit::getLearnerJourneyId)),
                unitNotes.stream().collect(Collectors.groupingBy(Note::getLearnerJourneyUnitId)),
                unitRepository.findByJourneyIdInOrderByOrder(journeyIds).stream()
                        .collect(Collectors.toMap(com.journey.feature.journey.entity.JourneyUnit::getId, com.journey.feature.journey.entity.JourneyUnit::getTitle)),
                byId(userRepository.findAllById(seniorIds), User::getId),
                byId(departmentRepository.findAll(), Department::getId));
    }

    private static <T> Map<String, T> byId(Iterable<T> values, Function<T, String> id) {
        Map<String, T> map = new java.util.HashMap<>();
        values.forEach(v -> map.put(id.apply(v), v));
        return map;
    }

    // ─── Rows ─────────────────────────────────────────────────────────────────

    private TeamLearnerDto row(User learner, Snapshot s, int attentionCount) {
        List<LearnerJourney> all = s.journeysByLearner().getOrDefault(learner.getId(), List.of());
        List<LearnerJourney> open = all.stream().filter(TeamService::isOpen).toList();
        int completed = (int) all.stream().filter(lj -> lj.getStatus() == ItemStatus.COMPLETED.getCode()).count();
        LocalDate today = LocalDate.now();

        int overdue = (int) open.stream().filter(lj -> isOverdue(lj, today)).count();
        int average = open.isEmpty() ? 0
                : (int) Math.round(open.stream().mapToInt(lj -> percent(lj, s)).average().orElse(0));
        LocalDateTime lastActivity = all.stream().map(lj -> lastActivity(lj, s)).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        LocalDate nextDue = open.stream().map(LearnerJourney::getDueDate).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);

        CurrentJourneyDto current = open.stream()
                .max(Comparator.comparing((LearnerJourney lj) -> lastActivity(lj, s), Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(lj -> new CurrentJourneyDto(lj.getId(), titleOf(lj, s), percent(lj, s),
                        ItemStatus.fromCode(lj.getStatus()).toDto(), lj.getDueDate()))
                .orElse(null);

        LearnerHealth health;
        if (open.isEmpty()) {
            health = completed > 0 ? LearnerHealth.DONE : LearnerHealth.NOT_STARTED;
        } else if (overdue > 0) {
            health = LearnerHealth.OVERDUE;
        } else if (isInactive(lastActivity) || open.stream().anyMatch(lj -> isDueSoonAndBehind(lj, s, today))) {
            health = LearnerHealth.AT_RISK;
        } else {
            health = LearnerHealth.ON_TRACK;
        }

        User senior = learner.getSeniorId() == null ? null : s.seniors().get(learner.getSeniorId());
        Department dept = s.departments().get(learner.getDepartmentId());
        return new TeamLearnerDto(
                learner.getId(), learner.getName(), learner.getEmail(),
                dept == null ? null : new DepartmentDto(dept.getId(), dept.getNameEn(), dept.getNameAr(), null),
                learner.getSeniorId(), senior == null ? null : senior.getName(),
                health.toDto(), open.size(), completed, overdue, average, current, lastActivity, nextDue, attentionCount);
    }

    private TeamKpisDto kpis(List<TeamLearnerDto> rows, List<AttentionItemDto> attention, Snapshot s) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        int completedThisMonth = (int) s.journeysByLearner().values().stream().flatMap(List::stream)
                .filter(lj -> lj.getStatus() == ItemStatus.COMPLETED.getCode())
                .filter(lj -> lj.getCompletedAt() != null && !lj.getCompletedAt().isBefore(monthStart))
                .count();
        int awaitingReview = (int) attention.stream()
                .filter(a -> a.type().code() == AttentionType.EXAM_TO_GRADE.getCode()
                        || a.type().code() == AttentionType.AWAITING_REVIEW.getCode())
                .count();
        return new TeamKpisDto(rows.size(),
                countHealth(rows, LearnerHealth.ON_TRACK),
                countHealth(rows, LearnerHealth.AT_RISK),
                countHealth(rows, LearnerHealth.OVERDUE),
                awaitingReview, completedThisMonth);
    }

    private static int countHealth(List<TeamLearnerDto> rows, LearnerHealth health) {
        return (int) rows.stream().filter(r -> r.health().code() == health.getCode()).count();
    }

    // ─── Attention ────────────────────────────────────────────────────────────

    private static final Comparator<AttentionItemDto> ATTENTION_ORDER =
            Comparator.comparingInt((AttentionItemDto a) -> a.type().code())
                    .thenComparing(AttentionItemDto::since, Comparator.nullsLast(Comparator.naturalOrder()));

    private List<AttentionItemDto> attentionFor(User learner, Snapshot s, boolean flagMissingSenior) {
        List<AttentionItemDto> out = new ArrayList<>();
        List<LearnerJourney> all = s.journeysByLearner().getOrDefault(learner.getId(), List.of());
        List<LearnerJourney> open = all.stream().filter(TeamService::isOpen).toList();
        LocalDate today = LocalDate.now();

        for (LearnerJourney lj : all) {
            if (lj.getStatus() == ItemStatus.CANCELLED.getCode()) continue;
            String title = titleOf(lj, s);
            String log = "/journey-log/" + lj.getId();

            ExamAttempt attempt = s.attemptByJourney().get(lj.getId());
            if (attempt != null && attempt.getStatus() == ExamAttemptStatus.SUBMITTED.getCode()) {
                out.add(item(AttentionType.EXAM_TO_GRADE, learner, title, null, "/exam/" + lj.getId(),
                        attempt.getSubmittedAt(), null, null));
            }
            if (!isOpen(lj)) continue;

            if (isOverdue(lj, today)) {
                out.add(item(AttentionType.OVERDUE, learner, title, null, log, null, lj.getDueDate(),
                        (int) ChronoUnit.DAYS.between(lj.getDueDate(), today)));
            }
            for (var unit : s.unitsByJourney().getOrDefault(lj.getId(), List.of())) {
                String unitTitle = s.unitTitles().getOrDefault(unit.getUnitId(), "");
                if (unit.getStatus() == ItemStatus.RESPONSE.getCode()) {
                    out.add(item(AttentionType.AWAITING_REVIEW, learner, title, unitTitle, log, unit.getUpdatedAt(), null, null));
                }
                s.notesByUnit().getOrDefault(unit.getId(), List.of()).stream()
                        .max(Comparator.comparing(Note::getTimestamp))
                        .filter(n -> learner.getId().equals(n.getActorId()))
                        .ifPresent(n -> out.add(item(AttentionType.UNANSWERED_NOTE, learner, title, unitTitle, log,
                                n.getTimestamp(), null, null)));
            }
            for (LearnerJourneyItem it : s.itemsByJourney().getOrDefault(lj.getId(), List.of())) {
                String itemTitle = itemTitleOf(it, s);
                // The learner spoke last and nobody has answered.
                s.notesByItem().getOrDefault(it.getId(), List.of()).stream()
                        .max(Comparator.comparing(Note::getTimestamp))
                        .filter(n -> learner.getId().equals(n.getActorId()))
                        .ifPresent(n -> out.add(item(AttentionType.UNANSWERED_NOTE, learner, title, itemTitle, log,
                                n.getTimestamp(), null, null)));
            }
        }

        // Once per learner, not per journey: someone busy on one journey isn't idle because another waits.
        LocalDateTime learnerLast = all.stream().map(lj -> lastActivity(lj, s)).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        if (!open.isEmpty() && isInactive(learnerLast)) {
            LearnerJourney current = open.stream()
                    .max(Comparator.comparing((LearnerJourney lj) -> lastActivity(lj, s), Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElseThrow();
            out.add(item(AttentionType.INACTIVE, learner, titleOf(current, s), null, "/journey-log/" + current.getId(),
                    learnerLast, current.getDueDate(), (int) ChronoUnit.DAYS.between(learnerLast.toLocalDate(), today)));
        }

        if (open.isEmpty() && all.stream().noneMatch(lj -> lj.getStatus() == ItemStatus.COMPLETED.getCode())) {
            out.add(item(AttentionType.NOTHING_ASSIGNED, learner, null, null, "/people/" + learner.getId(),
                    learner.getCreatedAt(), null, null));
        }
        if (flagMissingSenior && learner.getSeniorId() == null) {
            out.add(item(AttentionType.NO_SENIOR, learner, null, null, "/admin/users/" + learner.getId() + "/edit",
                    learner.getCreatedAt(), null, null));
        }
        return out;
    }

    private static AttentionItemDto item(AttentionType type, User learner, String journeyTitle, String itemTitle,
                                         String link, LocalDateTime since, LocalDate due, Integer days) {
        return new AttentionItemDto(type.toDto(), learner.getId(), learner.getName(), journeyTitle, itemTitle,
                link, since, due, days);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static boolean isOpen(LearnerJourney lj) {
        return lj.getStatus() != ItemStatus.COMPLETED.getCode() && lj.getStatus() != ItemStatus.CANCELLED.getCode();
    }

    private static boolean isOverdue(LearnerJourney lj, LocalDate today) {
        return isOpen(lj) && lj.getDueDate() != null && lj.getDueDate().isBefore(today);
    }

    private static boolean isInactive(LocalDateTime lastActivity) {
        return lastActivity != null && lastActivity.isBefore(LocalDateTime.now().minusDays(INACTIVE_DAYS));
    }

    private boolean isDueSoonAndBehind(LearnerJourney lj, Snapshot s, LocalDate today) {
        return lj.getDueDate() != null
                && !lj.getDueDate().isBefore(today)
                && !lj.getDueDate().isAfter(today.plusDays(DUE_SOON_DAYS))
                && percent(lj, s) < DUE_SOON_MIN_PROGRESS;
    }

    private static int percent(LearnerJourney lj, Snapshot s) {
        List<LearnerJourneyItem> items = s.itemsByJourney().getOrDefault(lj.getId(), List.of());
        if (items.isEmpty()) return 0;
        long done = items.stream().filter(i -> i.getStatus() == ItemStatus.COMPLETED.getCode()).count();
        return (int) Math.round((double) done / items.size() * 100);
    }

    /** Latest of: assignment, any item change (including time spent reading), any note, unit change, exam submission. */
    private static LocalDateTime lastActivity(LearnerJourney lj, Snapshot s) {
        List<LearnerJourneyItem> items = s.itemsByJourney().getOrDefault(lj.getId(), List.of());
        ExamAttempt attempt = s.attemptByJourney().get(lj.getId());
        return Stream.of(
                        Stream.of(lj.getAssignedAt()),
                        items.stream().map(LearnerJourneyItem::getUpdatedAt),
                        items.stream().flatMap(i -> s.notesByItem().getOrDefault(i.getId(), List.of()).stream()).map(Note::getTimestamp),
                        s.unitsByJourney().getOrDefault(lj.getId(), List.of()).stream()
                                .flatMap(u -> Stream.concat(Stream.of(u.getUpdatedAt()),
                                        s.notesByUnit().getOrDefault(u.getId(), List.of()).stream().map(Note::getTimestamp))),
                        Stream.of(attempt == null ? null : attempt.getSubmittedAt()))
                .flatMap(Function.identity())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private static String titleOf(LearnerJourney lj, Snapshot s) {
        Journey j = s.journeys().get(lj.getJourneyId());
        return j == null ? "" : j.getTitle();
    }

    private static String itemTitleOf(LearnerJourneyItem it, Snapshot s) {
        JourneyItem ji = s.journeyItems().get(it.getJourneyItemId());
        return ji == null ? "" : ji.getTitle();
    }
}
