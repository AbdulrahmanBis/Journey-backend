package com.journey.feature.learnerJourney.event;

import com.journey.feature.exam.event.ExamGradedEvent;
import com.journey.feature.learnerJourney.service.LearnerJourneyService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A journey with an exam completes when its units are completed <em>and</em> the exam is passed, so
 * grading can be the step that completes it. Runs inside the grading transaction.
 */
@Component
@RequiredArgsConstructor
public class JourneyCompletionListener {

    private final LearnerJourneyService learnerJourneys;

    @EventListener
    public void onExamGraded(ExamGradedEvent event) {
        learnerJourneys.recomputeJourneyStatus(event.learnerJourneyId());
    }
}
