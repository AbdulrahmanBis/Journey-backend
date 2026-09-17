package com.journey.feature.dashboard.service;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.enums.UserRole;
import com.journey.feature.dashboard.dto.LearnerSummaryDto;
import com.journey.feature.dashboard.dto.SeniorSummaryDto;
import com.journey.feature.journeyPackage.service.PackageAssignmentService;
import com.journey.feature.learnerJourney.dto.LearnerJourneyViewDto;
import com.journey.feature.learnerJourney.repository.LearnerJourneyRepository;
import com.journey.feature.learnerJourney.service.LearnerJourneyService;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.repository.UserRepository;
import com.journey.common.security.AccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final LearnerJourneyRepository learnerJourneyRepository;
    private final LearnerJourneyService learnerJourneyService;
    private final PackageAssignmentService packageAssignmentService;
    private final AccessPolicy access;

    /**
     * GET /api/dashboard/senior/:seniorId
     *
     * Returns every learner under this senior, each with their full list of journey views.
     */
    public List<LearnerSummaryDto> getSeniorOverview(String seniorId) {
        requireSeniorInReach(seniorId);
        return learnersOf(seniorId);
    }

    private List<LearnerSummaryDto> learnersOf(String seniorId) {
        List<User> learners = userRepository.findByRoleAndSeniorId(UserRole.LEARNER.getCode(), seniorId);
        return learners.stream()
                .map(this::buildLearnerSummary)
                .toList();
    }

    /**
     * GET /api/dashboard/manager
     *
     * Returns every senior with their learners nested inside.
     */
    public List<SeniorSummaryDto> getManagerOverview(String departmentId) {
        String scope = access.departmentScope(departmentId);
        List<User> seniors = scope == null
                ? userRepository.findByRole(UserRole.SENIOR.getCode())
                : userRepository.findByRoleAndDepartmentId(UserRole.SENIOR.getCode(), scope);
        // A department without seniors yet is a normal state, not an error.
        return seniors.stream()
                .map(senior -> {
                    List<LearnerSummaryDto> learnerSummaries = learnersOf(senior.getId());
                    return new SeniorSummaryDto(
                            senior.getId(), senior.getName(), senior.getEmail(),
                            UserRole.SENIOR.toDto(), senior.getCreatedAt(), learnerSummaries);
                })
                .toList();
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    /** The senior themselves, or someone who can see them (their Manager, HR, Admin). */
    private void requireSeniorInReach(String seniorId) {
        access.requireRole(AccessPolicy.STAFF);
        User senior = access.requireViewable(seniorId);
        if (!AccessPolicy.hasRole(senior, UserRole.SENIOR)) {
            throw new ApiException(ErrorCode.NOT_A_SENIOR, senior.getName());
        }
    }

    private LearnerSummaryDto buildLearnerSummary(User learner) {
        List<LearnerJourneyViewDto> journeyViews =
                learnerJourneyRepository.findByLearnerId(learner.getId()).stream()
                        .map(learnerJourneyService::buildView)
                        .toList();

        return new LearnerSummaryDto(
                learner.getId(), learner.getName(), learner.getEmail(),
                UserRole.LEARNER.toDto(), learner.getSeniorId(), learner.getCreatedAt(),
                journeyViews,
                packageAssignmentService.summariesFor(learner.getId()));
    }
}
