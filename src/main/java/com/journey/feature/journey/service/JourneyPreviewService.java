package com.journey.feature.journey.service;

import com.journey.common.security.AccessPolicy;
import com.journey.feature.exam.dto.ExamDto;
import com.journey.feature.exam.dto.ExamQuestionDto;
import com.journey.feature.exam.service.ExamService;
import com.journey.feature.journey.dto.JourneyItemDto;
import com.journey.feature.journey.dto.JourneyPreviewDto;
import com.journey.feature.journey.entity.JourneyUnit;
import com.journey.feature.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the preview of a journey. Nothing here reads or writes anyone's progress.
 *
 * <ul>
 *   <li><b>Staff</b> — every unit's content, quizzes and the exam with their answers.</li>
 *   <li><b>Learners</b> — the first unit's content as a sample; later units show item titles only; quiz and
 *       exam show how many questions there are, never the questions.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class JourneyPreviewService {

    private final JourneyService journeys;
    private final ExamService exams;
    private final AccessPolicy access;

    @Transactional(readOnly = true)
    public JourneyPreviewDto preview(String journeyId) {
        User actor = access.actor();
        boolean full = AccessPolicy.hasRole(actor, AccessPolicy.STAFF);
        var journey = journeys.getById(journeyId);

        List<JourneyItemDto> items = journeys.getItemsForJourney(journeyId);
        List<JourneyUnit> units = journeys.getUnitEntitiesForJourney(journeyId);
        List<JourneyPreviewDto.Unit> previewUnits = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            JourneyUnit unit = units.get(i);
            boolean open = full || i == 0;
            List<JourneyItemDto> unitItems = items.stream()
                    .filter(item -> unit.getId().equals(item.unitId()))
                    .map(item -> open ? item
                            : new JourneyItemDto(item.id(), item.journeyId(), item.unitId(), item.title(), null, item.order(), null))
                    .toList();
            List<ExamQuestionDto> quiz = journeys.getQuizForUnit(unit.getId()).stream().map(journeys::toQuizDto).toList();
            previewUnits.add(new JourneyPreviewDto.Unit(unit.getId(), unit.getTitle(), unit.getDescription(), unit.getOrder(),
                    !open, unitItems, quiz.size(), full ? quiz : null));
        }

        ExamDto exam = exams.getExam(journeyId);
        JourneyPreviewDto.Exam previewExam = exam == null ? null : new JourneyPreviewDto.Exam(
                exam.title(), exam.passingScorePercent(), exam.questions().size(), full ? exam.questions() : null);

        return new JourneyPreviewDto(journey, !full, previewUnits, previewExam);
    }
}
