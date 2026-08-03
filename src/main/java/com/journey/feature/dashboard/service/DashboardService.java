package com.journey.feature.dashboard.service;

import com.journey.common.enums.UserRole;
import com.journey.feature.dashboard.dto.LearnerSummaryDto;
import com.journey.feature.dashboard.dto.SeniorSummaryDto;
import com.journey.feature.learnerJourney.dto.LearnerJourneyViewDto;
import com.journey.feature.learnerJourney.repository.LearnerJourneyRepository;
import com.journey.feature.learnerJourney.service.LearnerJourneyService;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final LearnerJourneyRepository learnerJourneyRepository;
    private final LearnerJourneyService learnerJourneyService;

    /**
     * GET /api/dashboard/senior/:seniorId
     *
     * Returns every learner under this senior, each with their full list of journey views.
     * Mirrors JS: server.get('/api/learnersJourneysBySenior/:seniorId', ...)
     */
    public List<LearnerSummaryDto> getSeniorOverview(String seniorId) {
        List<User> learners = userRepository.findByRoleAndSeniorId(UserRole.LEARNER.getCode(), seniorId);
        return learners.stream()
                .map(this::buildLearnerSummary)
                .toList();
    }

    /**
     * GET /api/dashboard/manager
     *
     * Returns every senior with their learners nested inside.
     * Mirrors JS: server.get('/api/learnersJourneysByManager', ...)
     */
    public List<SeniorSummaryDto> getManagerOverview() {
        List<User> seniors = userRepository.findByRole(UserRole.SENIOR.getCode());
        if (seniors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No seniors found.");
        }
        return seniors.stream()
                .map(senior -> {
                    //TO
                    List<LearnerSummaryDto> learnerSummaries = getSeniorOverview(senior.getId());
                    return new SeniorSummaryDto(
                            senior.getId(), senior.getName(), senior.getEmail(),
                            UserRole.SENIOR, senior.getCreatedAt(), learnerSummaries);
                })
                .toList();
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private LearnerSummaryDto buildLearnerSummary(User learner) {
        // Load all LearnerJourney rows for this learner and build the full view for each
        List<LearnerJourneyViewDto> journeyViews =
                learnerJourneyRepository.findByLearnerId(learner.getId()).stream()
                        .map(learnerJourneyService::buildView)
                        .toList();

        return new LearnerSummaryDto(
                learner.getId(), learner.getName(), learner.getEmail(),
                UserRole.LEARNER, learner.getSeniorId(), learner.getCreatedAt(),
                journeyViews);
    }
}
