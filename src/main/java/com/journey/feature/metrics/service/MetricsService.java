package com.journey.feature.metrics.service;

import com.journey.common.enums.ItemStatus;
import com.journey.common.enums.UserRole;
import com.journey.feature.learnerJourney.dto.JourneyItemWithProgressDto;
import com.journey.feature.learnerJourney.dto.LearnerJourneyViewDto;
import com.journey.feature.learnerJourney.entity.LearnerJourney;
import com.journey.feature.learnerJourney.repository.LearnerJourneyRepository;
import com.journey.feature.learnerJourney.service.LearnerJourneyService;
import com.journey.feature.metrics.dto.*;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final UserRepository userRepository;
    private final LearnerJourneyRepository learnerJourneyRepository;
    private final LearnerJourneyService learnerJourneyService;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * GET /api/metrics/learner/:id
     * Mirrors JS /api/metrics when role === 'learner' (adapted to per-learner scope).
     */
    public LearnerMetricsDto getLearnerMetrics(String learnerId) {
        User learner = userRepository.findById(learnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<LearnerJourneyViewDto> views = getViewsForLearner(learnerId);
        return buildLearnerMetrics(learnerId, learner.getName(), views);
    }

    /**
     * GET /api/metrics/senior/:id
     * Mirrors JS /api/metrics when role === 'senior'.
     */
    public GroupMetricsDto getSeniorMetrics(String seniorId) {
        List<User> learners = userRepository.findByRoleAndSeniorId(UserRole.LEARNER.getCode(), seniorId);
        return buildGroupMetrics(learners);
    }

    /**
     * GET /api/metrics/org
     * Mirrors JS /api/metrics when role === 'manager' | 'admin'.
     */
    public OrgMetricsDto getOrgMetrics() {
        List<User> seniors = userRepository.findByRole(UserRole.SENIOR.getCode());
        if (seniors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No seniors found.");
        }

        List<User> allLearners = userRepository.findByRole(UserRole.LEARNER.getCode());
        List<LearnerJourneyViewDto> allViews = allLearners.stream()
                .flatMap(l -> getViewsForLearner(l.getId()).stream())
                .toList();

        GroupMetricsDto base = buildGroupMetricsFromViews(allLearners, allViews);

        // Per-senior breakdown
        List<OrgMetricsDto.PerSeniorRow> perSenior = seniors.stream().map(senior -> {
            List<User> teamLearners = userRepository.findByRoleAndSeniorId(UserRole.LEARNER.getCode(), senior.getId());
            List<LearnerJourneyViewDto> teamViews = teamLearners.stream()
                    .flatMap(l -> getViewsForLearner(l.getId()).stream())
                    .toList();
            ExamStatsDto examStats = buildExamStats(teamViews);
            double teamHours = round(teamViews.stream().mapToDouble(LearnerJourneyViewDto::totalTimeSpentHours).sum());
            int avgCompletion = teamViews.isEmpty() ? 0
                    : (int) Math.round(teamViews.stream().mapToInt(LearnerJourneyViewDto::percentComplete).average().orElse(0));

            return new OrgMetricsDto.PerSeniorRow(
                    senior.getId(), senior.getName(), teamLearners.size(),
                    teamHours, avgCompletion, examStats.passed(), examStats.failed());
        }).toList();

        return new OrgMetricsDto(
                base.learnerCount(), base.totalJourneys(), base.activeJourneys(),
                base.completedJourneys(), base.totalHours(), base.avgCompletionPercent(),
                base.exams(), base.hoursByDay(), base.hoursByMonth(), base.hoursByQuarter(),
                base.perLearner(), seniors.size(), perSenior);
    }

    // ─── Builders ─────────────────────────────────────────────────────────────

    private LearnerMetricsDto buildLearnerMetrics(String learnerId, String learnerName,
                                                  List<LearnerJourneyViewDto> views) {
        int total = views.size();
        int active = (int) views.stream()
                .filter(v -> !v.status().equals(ItemStatus.COMPLETED.getEnglish()) && !v.status().equals(ItemStatus.CANCELLED.getEnglish()))
                .count();
        int completed = (int) views.stream().filter(v -> v.status().equals(ItemStatus.COMPLETED.getEnglish())).count();
        int cancelled = (int) views.stream().filter(v -> v.status().equals(ItemStatus.CANCELLED.getEnglish())).count();

        List<JourneyItemWithProgressDto> allItems = views.stream()
                .flatMap(v -> v.items().stream()).toList();
        int totalItems = allItems.size();
        int completedItems = (int) allItems.stream()
                .filter(i -> i.progress() != null && i.progress().status().equals(ItemStatus.COMPLETED.getEnglish()))
                .count();
        int itemCompletionPct = totalItems == 0 ? 0
                : (int) Math.round((double) completedItems / totalItems * 100);

        double totalHours = round(views.stream().mapToDouble(LearnerJourneyViewDto::totalTimeSpentHours).sum());
        Double avgPerItem = completedItems == 0 ? null : round(totalHours / completedItems);

        ExamStatsDto examStats = buildExamStats(views);
        List<JourneyItemWithProgressDto> completedItemViews = allItems.stream()
                .filter(i -> i.progress() != null && i.progress().status().equals(ItemStatus.COMPLETED.getEnglish()))
                .toList();

        return new LearnerMetricsDto(
                learnerId, learnerName, total, active, completed, cancelled,
                totalItems, completedItems, itemCompletionPct, totalHours, avgPerItem,
                examStats,
                bucketByDay(completedItemViews, 14),
                bucketByMonth(completedItemViews, 6),
                bucketByQuarter(completedItemViews, 4));
    }

    private GroupMetricsDto buildGroupMetrics(List<User> learners) {
        List<LearnerJourneyViewDto> allViews = learners.stream()
                .flatMap(l -> getViewsForLearner(l.getId()).stream())
                .toList();
        return buildGroupMetricsFromViews(learners, allViews);
    }

    private GroupMetricsDto buildGroupMetricsFromViews(List<User> learners,
                                                       List<LearnerJourneyViewDto> allViews) {
        int learnerCount = learners.size();
        int totalJourneys = allViews.size();
        int activeJourneys = (int) allViews.stream()
                .filter(v -> !v.status().equals(ItemStatus.COMPLETED.getEnglish()) && !v.status().equals(ItemStatus.CANCELLED.getEnglish()))
                .count();
        int completedJourneys = (int) allViews.stream()
                .filter(v -> v.status().equals(ItemStatus.COMPLETED.getEnglish())).count();
        double totalHours = round(allViews.stream()
                .mapToDouble(LearnerJourneyViewDto::totalTimeSpentHours).sum());
        int avgCompletion = allViews.isEmpty() ? 0
                : (int) Math.round(allViews.stream()
                .mapToInt(LearnerJourneyViewDto::percentComplete).average().orElse(0));

        ExamStatsDto examStats = buildExamStats(allViews);

        List<JourneyItemWithProgressDto> allCompletedItems = allViews.stream()
                .flatMap(v -> v.items().stream())
                .filter(i -> i.progress() != null && i.progress().status().equals(ItemStatus.COMPLETED.getEnglish()))
                .toList();

        // Per-learner breakdown table
        List<GroupMetricsDto.PerLearnerRow> perLearner = learners.stream().map(learner -> {
            List<LearnerJourneyViewDto> lViews = allViews.stream()
                    .filter(v -> v.learnerId().equals(learner.getId())).toList();
            ExamStatsDto lExams = buildExamStats(lViews);
            return new GroupMetricsDto.PerLearnerRow(
                    learner.getId(), learner.getName(),
                    (int) lViews.stream().filter(v -> !v.status().equals(ItemStatus.COMPLETED.getEnglish()) && !v.status().equals(ItemStatus.CANCELLED.getEnglish())).count(),
                    (int) lViews.stream().filter(v -> v.status().equals(ItemStatus.COMPLETED.getEnglish())).count(),
                    round(lViews.stream().mapToDouble(LearnerJourneyViewDto::totalTimeSpentHours).sum()),
                    lViews.isEmpty() ? 0 : (int) Math.round(lViews.stream().mapToInt(LearnerJourneyViewDto::percentComplete).average().orElse(0)),
                    lExams.passed(), lExams.failed());
        }).toList();

        return new GroupMetricsDto(
                learnerCount, totalJourneys, activeJourneys, completedJourneys,
                totalHours, avgCompletion, examStats,
                bucketByDay(allCompletedItems, 14),
                bucketByMonth(allCompletedItems, 6),
                bucketByQuarter(allCompletedItems, 4),
                perLearner);
    }

    private ExamStatsDto buildExamStats(List<LearnerJourneyViewDto> views) {
        // Placeholder: exam stats require the exam feature.
        // When ExamService is added, inject it here and compute actual values.
        return new ExamStatsDto(0, 0, 0, 0, 0, 0, null);
    }

    // ─── Hours bucketing ──────────────────────────────────────────────────────

    /**
     * Groups timeSpentHours from completed items by their updatedAt date.
     * Each point is the updatedAt of the LearnerJourneyItem at the time it was completed.
     */
    private record HoursPoint(LocalDateTime updatedAt, double hours) {}

    private List<HoursPoint> toHoursPoints(List<JourneyItemWithProgressDto> items) {
        return items.stream()
                .filter(i -> i.progress() != null && i.progress().updatedAt() != null)
                .map(i -> new HoursPoint(
                        i.progress().updatedAt(),
                        i.progress().timeSpentHours() != null ? i.progress().timeSpentHours() : 0.0))
                .toList();
    }

    /** Last `days` calendar days, one bucket each. */
    private List<HoursBucketDto> bucketByDay(List<JourneyItemWithProgressDto> items, int days) {
        List<HoursPoint> points = toHoursPoints(items);
        LocalDate today = LocalDate.now();
        Map<LocalDate, Double> map = points.stream().collect(
                Collectors.groupingBy(p -> p.updatedAt().toLocalDate(),
                        Collectors.summingDouble(p -> p.hours)));

        List<HoursBucketDto> buckets = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String label = date.format(DateTimeFormatter.ofPattern("MMM d"));
            double hrs = round(map.getOrDefault(date, 0.0));
            buckets.add(new HoursBucketDto(label, hrs));
        }
        return buckets;
    }

    /** Last `months` calendar months, one bucket each. */
    private List<HoursBucketDto> bucketByMonth(List<JourneyItemWithProgressDto> items, int months) {
        List<HoursPoint> points = toHoursPoints(items);
        LocalDate today = LocalDate.now();
        List<HoursBucketDto> buckets = new ArrayList<>();

        for (int i = months - 1; i >= 0; i--) {
            LocalDate monthStart = today.withDayOfMonth(1).minusMonths(i);
            LocalDate monthEnd = monthStart.plusMonths(1);
            String label = monthStart.format(DateTimeFormatter.ofPattern("MMM ''yy"));

            double hrs = round(points.stream()
                    .filter(p -> {
                        LocalDate d = p.updatedAt().toLocalDate();
                        return !d.isBefore(monthStart) && d.isBefore(monthEnd);
                    })
                    .mapToDouble(p -> p.hours).sum());

            buckets.add(new HoursBucketDto(label, hrs));
        }
        return buckets;
    }

    /** Last `quarters` rolling 3-month windows, one bucket each. */
    private List<HoursBucketDto> bucketByQuarter(List<JourneyItemWithProgressDto> items, int quarters) {
        List<HoursPoint> points = toHoursPoints(items);
        LocalDate today = LocalDate.now();
        List<HoursBucketDto> buckets = new ArrayList<>();

        for (int i = quarters - 1; i >= 0; i--) {
            // end of this window = first day of (current month - i*3 months + 1 month)
            LocalDate windowEnd = today.withDayOfMonth(1).minusMonths((long) i * 3).plusMonths(1);
            LocalDate windowStart = windowEnd.minusMonths(3);

            String startLabel = windowStart.format(DateTimeFormatter.ofPattern("MMM"));
            String endLabel = windowEnd.minusDays(1).format(DateTimeFormatter.ofPattern("MMM ''yy"));
            String label = startLabel + "–" + endLabel;

            double hrs = round(points.stream()
                    .filter(p -> {
                        LocalDate d = p.updatedAt().toLocalDate();
                        return !d.isBefore(windowStart) && d.isBefore(windowEnd);
                    })
                    .mapToDouble(p -> p.hours).sum());

            buckets.add(new HoursBucketDto(label, hrs));
        }
        return buckets;
    }

    // ─── Utils ────────────────────────────────────────────────────────────────

    private List<LearnerJourneyViewDto> getViewsForLearner(String learnerId) {
        return learnerJourneyRepository.findByLearnerId(learnerId).stream()
                .map(learnerJourneyService::buildView)
                .toList();
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
