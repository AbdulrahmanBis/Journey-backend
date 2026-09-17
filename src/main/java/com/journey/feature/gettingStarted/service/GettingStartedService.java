package com.journey.feature.gettingStarted.service;

import com.journey.common.enums.ItemStatus;
import com.journey.common.enums.UserRole;
import com.journey.common.security.AccessPolicy;
import com.journey.feature.announcement.repository.AnnouncementRepository;
import com.journey.feature.department.entity.Department;
import com.journey.feature.department.repository.DepartmentRepository;
import com.journey.feature.gettingStarted.dto.GettingStartedDto;
import com.journey.feature.gettingStarted.dto.GettingStartedDto.Step;
import com.journey.feature.journeyPackage.repository.JourneyPackageRepository;
import com.journey.feature.learnerJourney.entity.LearnerJourney;
import com.journey.feature.learnerJourney.repository.LearnerJourneyRepository;
import com.journey.feature.learnerJourney.repository.LearnerJourneyUnitRepository;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * A few first steps for the people who run onboarding, each ticked from what is actually in the system —
 * nothing to tick by hand.
 *
 * <ul>
 *   <li><b>Manager</b> (their department) — seniors in place, every learner has a senior, something assigned,
 *       due dates on open work, an announcement posted.</li>
 *   <li><b>Senior</b> (their learners) — has learners, assigned them a journey, due dates set, reviewed a unit.</li>
 *   <li><b>HR</b> (the organisation) — every department has a manager, every learner has a senior, a package
 *       exists, every learner has something assigned, a company-wide announcement posted.</li>
 * </ul>
 * Admins and learners have no checklist. Hiding it is remembered on the account.
 */
@Service
@RequiredArgsConstructor
public class GettingStartedService {

    private static final Set<Integer> OPEN = Set.of(
            ItemStatus.NEW.getCode(), ItemStatus.REFLECT.getCode(), ItemStatus.RESPONSE.getCode());

    private final AccessPolicy access;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final LearnerJourneyRepository learnerJourneys;
    private final LearnerJourneyUnitRepository learnerUnits;
    private final JourneyPackageRepository packages;
    private final AnnouncementRepository announcements;

    @Transactional(readOnly = true)
    public GettingStartedDto forCaller() {
        User actor = access.actor();
        UserRole role = AccessPolicy.roleOf(actor);
        boolean dismissed = actor.getGettingStartedDismissedAt() != null;
        List<Step> steps = switch (role) {
            case MANAGER -> managerSteps(actor);
            case SENIOR -> seniorSteps(actor);
            case HR -> hrSteps(actor);
            default -> List.of();
        };
        return new GettingStartedDto(role.toDto(), dismissed, steps);
    }

    @Transactional
    public void dismiss() {
        User actor = access.actor();
        if (actor.getGettingStartedDismissedAt() == null) {
            actor.setGettingStartedDismissedAt(LocalDateTime.now());
            users.save(actor);
        }
    }

    // ─── Steps ──────────────────────────────────────────────────────────────────────────

    private List<Step> managerSteps(User manager) {
        String dept = manager.getDepartmentId();
        List<User> learners = users.findByRoleAndDepartmentId(UserRole.LEARNER.getCode(), dept);
        List<LearnerJourney> work = learnerJourneys.findByLearnerIdIn(ids(learners));
        return List.of(
                new Step("SENIORS", !users.findByRoleAndDepartmentId(UserRole.SENIOR.getCode(), dept).isEmpty(), "/admin/users/new"),
                new Step("LEARNERS_HAVE_SENIOR", everyoneHasSenior(learners), "/admin/users"),
                new Step("ASSIGNED", hasLiveWork(work), "/journeys"),
                new Step("DUE_DATES", openWorkHasDueDates(work), "/dashboard"),
                new Step("ANNOUNCEMENT", announcements.existsByAuthorId(manager.getId()), "/announcements/new"));
    }

    private List<Step> seniorSteps(User senior) {
        List<User> learners = users.findByRoleAndSeniorId(UserRole.LEARNER.getCode(), senior.getId());
        List<LearnerJourney> work = learnerJourneys.findByLearnerIdIn(ids(learners));
        boolean reviewed = !work.isEmpty() && learnerUnits.findByLearnerJourneyIdIn(work.stream().map(LearnerJourney::getId).toList())
                .stream().anyMatch(u -> u.getStatus() == ItemStatus.COMPLETED.getCode());
        return List.of(
                new Step("HAS_LEARNERS", !learners.isEmpty(), null),
                new Step("ASSIGNED", hasLiveWork(work), "/journeys"),
                new Step("DUE_DATES", openWorkHasDueDates(work), "/dashboard"),
                new Step("REVIEWED_UNIT", reviewed, "/dashboard"));
    }

    private List<Step> hrSteps(User hr) {
        List<Department> all = departments.findAll();
        boolean managersEverywhere = !all.isEmpty() && all.stream().allMatch(d ->
                users.countByDepartmentId(d.getId()) == 0
                        || !users.findByRoleAndDepartmentId(UserRole.MANAGER.getCode(), d.getId()).isEmpty()
                        || d.getId().equals(hr.getDepartmentId()));
        List<User> learners = users.findByRole(UserRole.LEARNER.getCode());
        List<LearnerJourney> work = learnerJourneys.findByLearnerIdIn(ids(learners));
        Set<String> withWork = new java.util.HashSet<>(work.stream()
                .filter(lj -> lj.getStatus() != ItemStatus.CANCELLED.getCode()).map(LearnerJourney::getLearnerId).toList());
        return List.of(
                new Step("DEPARTMENTS_HAVE_MANAGER", managersEverywhere, "/admin/users"),
                new Step("LEARNERS_HAVE_SENIOR", everyoneHasSenior(learners), "/admin/users"),
                new Step("PACKAGE", packages.count() > 0, "/journeys/packages/new"),
                new Step("EVERYONE_ASSIGNED", !learners.isEmpty() && learners.stream().allMatch(l -> withWork.contains(l.getId())), "/dashboard"),
                new Step("ORG_ANNOUNCEMENT", announcements.existsByAuthorIdAndOrgWideTrue(hr.getId()), "/announcements/new"));
    }

    // ─── Checks ─────────────────────────────────────────────────────────────────────────

    private static List<String> ids(List<User> people) {
        return people.stream().map(User::getId).toList();
    }

    private static boolean everyoneHasSenior(List<User> learners) {
        return !learners.isEmpty() && learners.stream().allMatch(l -> l.getSeniorId() != null);
    }

    private static boolean hasLiveWork(List<LearnerJourney> work) {
        return work.stream().anyMatch(lj -> lj.getStatus() != ItemStatus.CANCELLED.getCode());
    }

    /** Every open journey has a due date — and there is at least one open journey to judge. */
    private static boolean openWorkHasDueDates(List<LearnerJourney> work) {
        List<LearnerJourney> open = work.stream().filter(lj -> OPEN.contains(lj.getStatus())).toList();
        return !open.isEmpty() && open.stream().allMatch(lj -> lj.getDueDate() != null);
    }
}
