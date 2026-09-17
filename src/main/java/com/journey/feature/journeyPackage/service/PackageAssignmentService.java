package com.journey.feature.journeyPackage.service;

import com.journey.common.enums.ItemStatus;
import com.journey.common.enums.UserRole;
import com.journey.common.security.AccessPolicy;
import com.journey.common.service.IdGeneratorService;
import com.journey.feature.journey.entity.Journey;
import com.journey.feature.journey.service.JourneyService;
import com.journey.feature.journeyPackage.dto.AssignPackageRequest;
import com.journey.feature.journeyPackage.dto.PackageAssignmentDto;
import com.journey.feature.journeyPackage.dto.PackageContextDto;
import com.journey.feature.journeyPackage.dto.PackageJourneyProgressDto;
import com.journey.feature.journeyPackage.entity.JourneyPackage;
import com.journey.feature.journeyPackage.entity.PackageAssignment;
import com.journey.feature.journeyPackage.entity.PackageAssignmentJourney;
import com.journey.feature.journeyPackage.entity.PackageJourney;
import com.journey.feature.journeyPackage.event.PackageAssignedEvent;
import com.journey.feature.journeyPackage.event.PackageCancelledEvent;
import com.journey.feature.journeyPackage.repository.PackageAssignmentJourneyRepository;
import com.journey.feature.journeyPackage.repository.PackageAssignmentRepository;
import com.journey.feature.learnerJourney.entity.LearnerJourney;
import com.journey.feature.learnerJourney.service.LearnerJourneyService;
import com.journey.feature.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Packages given to learners. A package assignment is only a grouping over ordinary learner
 * journeys: every journey keeps its own log, items, exam and notes, and nothing in the journey
 * feature knows packages exist.
 *
 * <p>Access mirrors single journeys: staff assign to learners they can see; Manager, HR or Admin
 * cancel; anyone who can see the learner can read.
 */
@Service
@RequiredArgsConstructor
public class PackageAssignmentService {

    private final PackageAssignmentRepository assignmentRepository;
    private final PackageAssignmentJourneyRepository assignmentJourneyRepository;
    private final PackageService packageService;
    private final LearnerJourneyService learnerJourneyService;
    private final JourneyService journeyService;
    private final IdGeneratorService idGenerator;
    private final ApplicationEventPublisher events;
    private final AccessPolicy access;

    // ─── Reads ────────────────────────────────────────────────────────────────

    public List<PackageAssignmentDto> listForLearner(String learnerId) {
        access.requireViewable(learnerId);
        return summariesFor(learnerId);
    }

    /** No access check: for callers that have already checked the learner (the dashboard). */
    public List<PackageAssignmentDto> summariesFor(String learnerId) {
        return assignmentRepository.findByLearnerIdOrderByAssignedAtDesc(learnerId).stream()
                .map(this::toDto)
                .toList();
    }

    public PackageAssignmentDto get(String id) {
        PackageAssignment pa = requireAssignment(id);
        access.requireViewable(pa.getLearnerId());
        return toDto(pa);
    }

    /** The live packages a learner journey belongs to, each with its "next journey". */
    public List<PackageContextDto> contextFor(String learnerJourneyId) {
        LearnerJourney lj = learnerJourneyService.getEntity(learnerJourneyId);
        access.requireViewable(lj.getLearnerId());

        List<PackageContextDto> contexts = new ArrayList<>();
        for (PackageAssignmentJourney link : assignmentJourneyRepository.findByLearnerJourneyId(learnerJourneyId)) {
            PackageAssignment pa = requireAssignment(link.getPackageAssignmentId());
            if (pa.getCancelledAt() != null) continue;

            List<PackageJourneyProgressDto> journeys = progressOf(pa);
            int index = indexOf(journeys, learnerJourneyId);
            PackageJourneyProgressDto next = nextUnfinished(journeys, index);
            contexts.add(new PackageContextDto(
                    pa.getId(),
                    packageService.requirePackage(pa.getPackageId()).getTitle(),
                    index + 1,
                    journeys.size(),
                    (int) journeys.stream().filter(PackageAssignmentService::isCompleted).count(),
                    next == null ? null : next.learnerJourneyId(),
                    next == null ? null : next.title()));
        }
        return contexts;
    }

    // ─── Mutations ────────────────────────────────────────────────────────────

    /**
     * Gives the package to a learner. For each journey, in order: a live assignment the learner
     * already has (in progress or completed) is reused; otherwise a new one is created. A cancelled
     * one counts as none. One notification goes out for the whole package.
     */
    @Transactional
    public PackageAssignmentDto assign(AssignPackageRequest req) {
        User actor = access.requireRole(AccessPolicy.STAFF);
        User learner = access.requireViewable(req.learnerId());
        if (!AccessPolicy.hasRole(learner, UserRole.LEARNER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, learner.getName() + " is not a Learner.");
        }
        LearnerJourneyService.requireNotPast(req.dueDate());
        return toDto(createAssignment(packageService.requirePackage(req.packageId()), learner, actor, false, req.dueDate()));
    }

    /**
     * Self-enrollment from the catalog. The caller (the catalog) has already checked that the learner
     * is enrolling themselves and picked their reviewer, who is recorded as the assigner.
     */
    @Transactional
    public PackageAssignment enrollSelf(String packageId, User learner, User reviewer) {
        return createAssignment(packageService.requirePackage(packageId), learner, reviewer, true, null);
    }

    /**
     * @param dueDate null → today + the package's target days, or no deadline if it has none. Journeys the
     *                package creates share the package's due date; without one they fall back to their own.
     */
    private PackageAssignment createAssignment(JourneyPackage pkg, User learner, User actor, boolean selfEnrolled,
                                               LocalDate dueDate) {
        LocalDate due = dueDate != null ? dueDate
                : pkg.getTargetDays() == null ? null : LocalDate.now().plusDays(pkg.getTargetDays());
        List<PackageJourney> definition = packageService.journeysOf(pkg.getId());
        if (definition.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This package has no journeys.");
        }
        if (assignmentRepository.existsByPackageIdAndLearnerIdAndCancelledAtIsNull(pkg.getId(), learner.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This learner already has that package.");
        }

        PackageAssignment pa = assignmentRepository.save(PackageAssignment.builder()
                .id(idGenerator.next(IdGeneratorService.PACKAGE_ASSIGNMENT, "pa-"))
                .packageId(pkg.getId())
                .learnerId(learner.getId())
                .assignedById(actor.getId())
                .assignedByName(actor.getName())
                .selfEnrolled(selfEnrolled)
                .dueDate(due)
                .build());

        for (PackageJourney pj : definition) {
            LearnerJourney existing = learnerJourneyService
                    .findActiveAssignment(pj.getJourneyId(), learner.getId())
                    .orElse(null);
            LearnerJourney lj = existing != null
                    ? existing
                    : learnerJourneyService.createAssignment(pj.getJourneyId(), learner, actor, selfEnrolled, due);
            assignmentJourneyRepository.save(PackageAssignmentJourney.builder()
                    .packageAssignmentId(pa.getId())
                    .learnerJourneyId(lj.getId())
                    .position(pj.getPosition())
                    .createdByPackage(existing == null)
                    .build());
        }

        // A self-enrollment is announced to the reviewer by the catalog, not to the learner.
        if (!selfEnrolled) {
            events.publishEvent(new PackageAssignedEvent(
                    pa.getId(), learner.getId(), actor.getId(), actor.getName(), pkg.getTitle(), definition.size()));
        }
        return pa;
    }

    /** The learner's live (not cancelled) assignment of a package, if any. */
    public Optional<PackageAssignment> findActiveAssignment(String packageId, String learnerId) {
        return assignmentRepository.findFirstByPackageIdAndLearnerIdAndCancelledAtIsNull(packageId, learnerId);
    }

    /** Most recent assignment of a package for a learner, cancelled or not. */
    public Optional<PackageAssignment> findLatestAssignment(String packageId, String learnerId) {
        return assignmentRepository.findFirstByPackageIdAndLearnerIdOrderByAssignedAtDesc(packageId, learnerId);
    }

    public PackageAssignmentDto toSummary(PackageAssignment pa) {
        return toDto(pa);
    }

    /**
     * Cancels the package and the journeys it created that aren't finished. Journeys the learner
     * already had before the package are left alone, as are finished ones and ones another live
     * package still includes.
     */
    @Transactional
    public PackageAssignmentDto cancel(String id) {
        access.requireRole(UserRole.MANAGER, UserRole.HR, UserRole.ADMIN);
        PackageAssignment pa = requireAssignment(id);
        access.requireViewable(pa.getLearnerId());
        if (pa.getCancelledAt() != null) return toDto(pa);

        pa.setCancelledAt(LocalDateTime.now());
        assignmentRepository.save(pa);

        for (PackageAssignmentJourney link : assignmentJourneyRepository.findByPackageAssignmentIdOrderByPosition(id)) {
            if (!link.getCreatedByPackage()) continue;
            LearnerJourney lj = learnerJourneyService.getEntity(link.getLearnerJourneyId());
            boolean finished = lj.getStatus() == ItemStatus.COMPLETED.getCode()
                    || lj.getStatus() == ItemStatus.CANCELLED.getCode();
            if (!finished && !usedByAnotherLivePackage(lj.getId(), id)) {
                learnerJourneyService.cancelQuietly(lj.getId());
            }
        }

        events.publishEvent(new PackageCancelledEvent(
                pa.getId(), pa.getLearnerId(), packageService.requirePackage(pa.getPackageId()).getTitle()));
        return toDto(pa);
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private PackageAssignmentDto toDto(PackageAssignment pa) {
        JourneyPackage pkg = packageService.requirePackage(pa.getPackageId());
        List<PackageJourneyProgressDto> journeys = progressOf(pa);
        List<PackageJourneyProgressDto> live = journeys.stream()
                .filter(j -> j.status().code() != ItemStatus.CANCELLED.getCode())
                .toList();

        int completed = (int) live.stream().filter(PackageAssignmentService::isCompleted).count();
        int percent = live.isEmpty() ? 0
                : (int) Math.round(live.stream().mapToInt(PackageJourneyProgressDto::percentComplete).average().orElse(0));

        ItemStatus status;
        if (pa.getCancelledAt() != null) status = ItemStatus.CANCELLED;
        else if (!live.isEmpty() && completed == live.size()) status = ItemStatus.COMPLETED;
        else if (live.stream().anyMatch(j -> j.status().code() != ItemStatus.NEW.getCode())) status = ItemStatus.REFLECT;
        else status = ItemStatus.NEW;

        return new PackageAssignmentDto(pa.getId(), pkg.getId(), pkg.getTitle(), pkg.getDescription(),
                pa.getLearnerId(), pa.getAssignedById(), pa.getAssignedByName(), pa.getAssignedAt(), pa.getDueDate(),
                Boolean.TRUE.equals(pa.getSelfEnrolled()),
                pa.getCancelledAt(), status.toDto(), percent, completed, live.size(), journeys);
    }

    private List<PackageJourneyProgressDto> progressOf(PackageAssignment pa) {
        return assignmentJourneyRepository.findByPackageAssignmentIdOrderByPosition(pa.getId()).stream()
                .map(link -> {
                    LearnerJourney lj = learnerJourneyService.getEntity(link.getLearnerJourneyId());
                    Journey j = journeyService.getEntityById(lj.getJourneyId());
                    return new PackageJourneyProgressDto(lj.getId(), j.getId(), j.getTitle(), j.getTechTag(),
                            link.getPosition(), ItemStatus.fromCode(lj.getStatus()).toDto(),
                            learnerJourneyService.percentComplete(lj.getId()));
                })
                .toList();
    }

    /** The first unfinished, uncancelled journey after {@code index}, wrapping round; null if none. */
    private static PackageJourneyProgressDto nextUnfinished(List<PackageJourneyProgressDto> journeys, int index) {
        for (int step = 1; step < journeys.size(); step++) {
            PackageJourneyProgressDto candidate = journeys.get((index + step) % journeys.size());
            if (!isCompleted(candidate) && candidate.status().code() != ItemStatus.CANCELLED.getCode()) {
                return candidate;
            }
        }
        return null;
    }

    private static int indexOf(List<PackageJourneyProgressDto> journeys, String learnerJourneyId) {
        for (int i = 0; i < journeys.size(); i++) {
            if (journeys.get(i).learnerJourneyId().equals(learnerJourneyId)) return i;
        }
        return 0;
    }

    private static boolean isCompleted(PackageJourneyProgressDto j) {
        return j.status().code() == ItemStatus.COMPLETED.getCode();
    }

    private boolean usedByAnotherLivePackage(String learnerJourneyId, String exceptAssignmentId) {
        return assignmentJourneyRepository.findByLearnerJourneyId(learnerJourneyId).stream()
                .filter(link -> !link.getPackageAssignmentId().equals(exceptAssignmentId))
                .map(link -> requireAssignment(link.getPackageAssignmentId()))
                .anyMatch(other -> other.getCancelledAt() == null);
    }

    private PackageAssignment requireAssignment(String id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Package assignment not found: " + id));
    }
}
