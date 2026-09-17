package com.journey.feature.catalog.service;

import com.journey.common.enums.CatalogItemType;
import com.journey.common.enums.ItemStatus;
import com.journey.common.enums.LearningStatus;
import com.journey.common.enums.UserRole;
import com.journey.common.security.AccessPolicy;
import com.journey.feature.catalog.dto.CatalogDetailDto;
import com.journey.feature.catalog.dto.CatalogEntryDto;
import com.journey.feature.catalog.dto.CatalogPageDto;
import com.journey.feature.catalog.dto.EnrollRequest;
import com.journey.feature.catalog.dto.EnrollResultDto;
import com.journey.feature.catalog.dto.FacetDto;
import com.journey.feature.catalog.dto.MyProgressDto;
import com.journey.feature.catalog.dto.SyllabusEntryDto;
import com.journey.feature.catalog.dto.SyllabusUnitDto;
import com.journey.feature.catalog.event.SelfEnrolledEvent;
import com.journey.feature.exam.repository.ExamRepository;
import com.journey.feature.journey.dto.JourneyDto;
import com.journey.feature.journey.entity.JourneyItem;
import com.journey.feature.journey.repository.JourneyItemRepository;
import com.journey.feature.journey.service.JourneyService;
import com.journey.feature.journeyPackage.dto.PackageAssignmentDto;
import com.journey.feature.journeyPackage.dto.PackageDto;
import com.journey.feature.journeyPackage.dto.PackageJourneyDto;
import com.journey.feature.journeyPackage.entity.PackageAssignment;
import com.journey.feature.journeyPackage.repository.PackageAssignmentRepository;
import com.journey.feature.journeyPackage.service.PackageAssignmentService;
import com.journey.feature.journeyPackage.service.PackageService;
import com.journey.feature.learnerJourney.entity.LearnerJourney;
import com.journey.feature.learnerJourney.repository.LearnerJourneyRepository;
import com.journey.feature.learnerJourney.service.LearnerJourneyService;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The catalog: every journey and package, for everyone, with search, facets and self-enrollment.
 *
 * <p>Everything is assembled in memory per request. That is plenty for an organisation's own
 * library (tens to hundreds of entries); past that, move the search text into a full-text index
 * and the facet counts into queries.
 *
 * <p>Facets follow the usual rule: each facet's counts apply every other active filter but not its
 * own, so choosing a tag still shows how many results each other tag would give.
 *
 * <p><b>Self-enrollment.</b> Only learners enroll themselves; staff assign from the same page. The
 * enrollment records the learner's reviewer as the assigner (their senior, else a manager of their
 * department) and flags it self-enrolled. Every review and notification route reads the assigner,
 * so recording the learner there would send their own exam and notes back to them.
 */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final JourneyService journeyService;
    private final JourneyItemRepository journeyItemRepository;
    private final ExamRepository examRepository;
    private final PackageService packageService;
    private final PackageAssignmentService packageAssignmentService;
    private final PackageAssignmentRepository packageAssignmentRepository;
    private final LearnerJourneyService learnerJourneyService;
    private final LearnerJourneyRepository learnerJourneyRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final AccessPolicy access;

    /** An entry plus what filtering and ranking need. */
    private record Candidate(CatalogEntryDto entry, String titleText, String searchText, Set<String> tags) {
        int typeCode() { return entry.type().code(); }
        Integer statusCode() { return entry.mine() == null ? null : entry.mine().status().code(); }
    }

    // ─── Browse ───────────────────────────────────────────────────────────────

    public CatalogPageDto browse(String q, Integer type, String tag, Integer status, String sort) {
        User actor = access.actor();
        boolean learner = AccessPolicy.hasRole(actor, UserRole.LEARNER);
        List<String> terms = terms(q);
        List<Candidate> all = candidates(learner ? actor : null);

        Predicate<Candidate> byQuery = c -> terms.stream().allMatch(c.searchText()::contains);
        Predicate<Candidate> byType = c -> type == null || c.typeCode() == type;
        Predicate<Candidate> byTag = c -> tag == null || tag.isBlank() || c.tags().contains(tag.toLowerCase(Locale.ROOT));
        Predicate<Candidate> byStatus = c -> status == null || !learner || Objects.equals(c.statusCode(), status);

        List<Candidate> matched = all.stream()
                .filter(byQuery.and(byType).and(byTag).and(byStatus))
                .sorted(comparator(sort, terms))
                .toList();

        List<FacetDto> types = Stream.of(CatalogItemType.values())
                .map(t -> new FacetDto(String.valueOf(t.getCode()), t.toDto(),
                        count(all, byQuery.and(byTag).and(byStatus).and(c -> c.typeCode() == t.getCode()))))
                .toList();

        // Tags keep the author's spelling; matching ignores case.
        Map<String, String> tagSpelling = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        all.forEach(c -> c.entry().tags().forEach(t -> tagSpelling.putIfAbsent(t, t)));
        List<FacetDto> tags = tagSpelling.values().stream()
                .map(t -> new FacetDto(t, null, count(all, byQuery.and(byType).and(byStatus)
                        .and(c -> c.tags().contains(t.toLowerCase(Locale.ROOT))))))
                .toList();

        List<FacetDto> statuses = !learner ? List.of() : Stream.of(LearningStatus.values())
                .map(s -> new FacetDto(String.valueOf(s.getCode()), s.toDto(),
                        count(all, byQuery.and(byType).and(byTag).and(c -> Objects.equals(c.statusCode(), s.getCode())))))
                .toList();

        return new CatalogPageDto(
                matched.stream().map(Candidate::entry).toList(),
                matched.size(), types, tags, statuses,
                learner ? reviewerNameOf(actor) : null);
    }

    // ─── Detail ───────────────────────────────────────────────────────────────

    public CatalogDetailDto journeyDetail(String journeyId) {
        User actor = access.actor();
        JourneyDto journey = journeyService.getById(journeyId);
        User learner = AccessPolicy.hasRole(actor, UserRole.LEARNER) ? actor : null;
        List<JourneyItem> items = journeyService.getItemEntitiesForJourney(journeyId);
        List<SyllabusUnitDto> units = journeyService.getUnitEntitiesForJourney(journeyId).stream()
                .map(unit -> new SyllabusUnitDto(unit.getOrder(), unit.getTitle(), unit.getDescription(),
                        items.stream()
                                .filter(item -> unit.getId().equals(item.getUnitId()))
                                .map(item -> new SyllabusEntryDto(item.getOrder(), item.getTitle(), item.getDescription(), null, null, null))
                                .toList(),
                        journeyService.getQuizForUnit(unit.getId()).size()))
                .toList();
        return new CatalogDetailDto(journeyCandidate(journey, learner).entry(), null, units,
                learner == null ? null : reviewerNameOf(learner));
    }

    public CatalogDetailDto packageDetail(String packageId) {
        User actor = access.actor();
        PackageDto pkg = packageService.get(packageId);
        User learner = AccessPolicy.hasRole(actor, UserRole.LEARNER) ? actor : null;
        List<SyllabusEntryDto> syllabus = pkg.journeys().stream()
                .map(pj -> {
                    JourneyDto j = journeyService.getById(pj.journeyId());
                    return new SyllabusEntryDto(pj.position(), j.title(), j.description(), j.techTag(),
                            (int) journeyItemRepository.countByJourneyId(j.id()), j.id());
                })
                .toList();
        return new CatalogDetailDto(packageCandidate(pkg, learner).entry(), syllabus, null,
                learner == null ? null : reviewerNameOf(learner));
    }

    // ─── Enroll ───────────────────────────────────────────────────────────────

    @Transactional
    public EnrollResultDto enroll(EnrollRequest req) {
        User learner = access.requireRole(UserRole.LEARNER);
        User reviewer = reviewerOf(learner);
        if (reviewer == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You don't have a senior or a manager yet, so nobody could review your work. Ask HR to set one.");
        }
        CatalogItemType type;
        try {
            type = CatalogItemType.fromCode(req.type());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown catalog type: " + req.type());
        }

        if (type == CatalogItemType.JOURNEY) {
            JourneyDto journey = journeyService.getById(req.id());
            if (learnerJourneyService.findActiveAssignment(journey.id(), learner.getId()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already enrolled in this journey.");
            }
            LearnerJourney lj = learnerJourneyService.createAssignment(journey.id(), learner, reviewer, true);
            events.publishEvent(new SelfEnrolledEvent(learner.getId(), learner.getName(), reviewer.getId(),
                    journey.title(), type.getEnglish(), type.getArabic(), "/journey-log/" + lj.getId()));
            return new EnrollResultDto(lj.getId(), null);
        }

        PackageDto pkg = packageService.get(req.id());
        if (packageAssignmentService.findActiveAssignment(pkg.id(), learner.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already enrolled in this package.");
        }
        PackageAssignment pa = packageAssignmentService.enrollSelf(pkg.id(), learner, reviewer);
        PackageAssignmentDto summary = packageAssignmentService.toSummary(pa);
        events.publishEvent(new SelfEnrolledEvent(learner.getId(), learner.getName(), reviewer.getId(),
                pkg.title(), type.getEnglish(), type.getArabic(), "/dashboard"));
        String first = summary.journeys().isEmpty() ? null : summary.journeys().get(0).learnerJourneyId();
        return new EnrollResultDto(first, pa.getId());
    }

    // ─── Building entries ─────────────────────────────────────────────────────

    private List<Candidate> candidates(User learner) {
        List<Candidate> list = new ArrayList<>();
        journeyService.getAllJourneys().forEach(j -> list.add(journeyCandidate(j, learner)));
        packageService.list().forEach(p -> list.add(packageCandidate(p, learner)));
        return list;
    }

    private Candidate journeyCandidate(JourneyDto j, User learner) {
        List<JourneyItem> items = journeyService.getItemEntitiesForJourney(j.id());
        List<String> tags = j.techTag() == null || j.techTag().isBlank() ? List.of() : List.of(j.techTag());
        CatalogEntryDto entry = new CatalogEntryDto(
                CatalogItemType.JOURNEY.toDto(), j.id(), j.title(), j.description(), tags,
                items.size(),
                j.targetDays(),
                examRepository.findByJourneyId(j.id()).isPresent(),
                null,
                learnerJourneyRepository.countByJourneyIdAndStatusNot(j.id(), ItemStatus.CANCELLED.getCode()),
                j.createdByName(), j.updatedAt(),
                learner == null ? null : myJourneyProgress(j.id(), learner));
        String text = join(Stream.concat(Stream.of(j.title(), j.description(), j.techTag()),
                items.stream().map(JourneyItem::getTitle)));
        return new Candidate(entry, lower(j.title()), text, lowerSet(tags));
    }

    private Candidate packageCandidate(PackageDto p, User learner) {
        List<String> tags = p.journeys().stream()
                .map(PackageJourneyDto::techTag)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream().toList();
        List<String> titles = p.journeys().stream().map(PackageJourneyDto::title).toList();
        boolean anyExam = p.journeys().stream()
                .anyMatch(pj -> examRepository.findByJourneyId(pj.journeyId()).isPresent());
        CatalogEntryDto entry = new CatalogEntryDto(
                CatalogItemType.PACKAGE.toDto(), p.id(), p.title(), p.description(), tags,
                p.journeys().size(), p.targetDays(), anyExam, titles,
                packageAssignmentRepository.countByPackageIdAndCancelledAtIsNull(p.id()),
                p.createdByName(), p.updatedAt(),
                learner == null ? null : myPackageProgress(p.id(), learner));
        String text = join(Stream.concat(Stream.of(p.title(), p.description()),
                Stream.concat(titles.stream(), tags.stream())));
        return new Candidate(entry, lower(p.title()), text, lowerSet(tags));
    }

    private MyProgressDto myJourneyProgress(String journeyId, User learner) {
        LearnerJourney live = learnerJourneyService.findActiveAssignment(journeyId, learner.getId()).orElse(null);
        if (live != null) {
            LearningStatus status = live.getStatus() == ItemStatus.COMPLETED.getCode()
                    ? LearningStatus.COMPLETED : LearningStatus.IN_PROGRESS;
            return new MyProgressDto(status.toDto(), learnerJourneyService.percentComplete(live.getId()), live.getId(), null);
        }
        return learnerJourneyRepository.findFirstByJourneyIdAndLearnerIdOrderByAssignedAtDesc(journeyId, learner.getId())
                .map(last -> new MyProgressDto(LearningStatus.CANCELLED.toDto(),
                        learnerJourneyService.percentComplete(last.getId()), last.getId(), null))
                .orElse(new MyProgressDto(LearningStatus.NOT_STARTED.toDto(), 0, null, null));
    }

    private MyProgressDto myPackageProgress(String packageId, User learner) {
        PackageAssignment live = packageAssignmentService.findActiveAssignment(packageId, learner.getId()).orElse(null);
        if (live != null) {
            PackageAssignmentDto summary = packageAssignmentService.toSummary(live);
            LearningStatus status = summary.status().code() == ItemStatus.COMPLETED.getCode()
                    ? LearningStatus.COMPLETED : LearningStatus.IN_PROGRESS;
            return new MyProgressDto(status.toDto(), summary.percentComplete(), null, live.getId());
        }
        return packageAssignmentService.findLatestAssignment(packageId, learner.getId())
                .map(last -> new MyProgressDto(LearningStatus.CANCELLED.toDto(), 0, null, last.getId()))
                .orElse(new MyProgressDto(LearningStatus.NOT_STARTED.toDto(), 0, null, null));
    }

    // ─── Reviewer ─────────────────────────────────────────────────────────────

    /** The learner's senior; failing that, a manager of their department (by name, for stability). */
    private User reviewerOf(User learner) {
        if (learner.getSeniorId() != null) {
            User senior = userRepository.findById(learner.getSeniorId()).orElse(null);
            if (senior != null) return senior;
        }
        return userRepository.findByRoleAndDepartmentId(UserRole.MANAGER.getCode(), learner.getDepartmentId()).stream()
                .min(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
                .orElse(null);
    }

    private String reviewerNameOf(User learner) {
        User reviewer = reviewerOf(learner);
        return reviewer == null ? null : reviewer.getName();
    }

    // ─── Ranking & text ───────────────────────────────────────────────────────

    private static Comparator<Candidate> comparator(String sort, List<String> terms) {
        Comparator<Candidate> byTitle = Comparator.comparing(Candidate::titleText);
        String key = sort == null || sort.isBlank() ? (terms.isEmpty() ? "title" : "relevance") : sort;
        return switch (key) {
            case "newest" -> Comparator.comparing((Candidate c) -> c.entry().updatedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(byTitle);
            case "popular" -> Comparator.comparingLong((Candidate c) -> c.entry().learnerCount()).reversed().thenComparing(byTitle);
            case "relevance" -> Comparator.comparingInt((Candidate c) -> score(c, terms)).reversed().thenComparing(byTitle);
            default -> byTitle;
        };
    }

    /** Title hits outrank tag hits, which outrank hits in descriptions and outlines. */
    private static int score(Candidate c, List<String> terms) {
        int score = 0;
        for (String term : terms) {
            if (c.titleText().startsWith(term)) score += 4;
            else if (c.titleText().contains(term)) score += 3;
            if (c.tags().stream().anyMatch(t -> t.contains(term))) score += 2;
            score += 1; // it matched somewhere, or it wouldn't be here
        }
        return score;
    }

    private static List<String> terms(String q) {
        if (q == null || q.isBlank()) return List.of();
        return Stream.of(q.toLowerCase(Locale.ROOT).trim().split("\\s+")).filter(t -> !t.isBlank()).toList();
    }

    private static long count(List<Candidate> all, Predicate<Candidate> filter) {
        return all.stream().filter(filter).count();
    }

    private static String join(Stream<String> parts) {
        return parts.filter(Objects::nonNull).map(CatalogService::lower).collect(Collectors.joining(" \n "));
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static Set<String> lowerSet(List<String> values) {
        return values.stream().map(CatalogService::lower).collect(Collectors.toSet());
    }
}
