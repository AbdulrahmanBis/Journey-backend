package com.journey.feature.exam.event;

import com.journey.feature.journey.repository.JourneyRepository;
import com.journey.feature.learnerJourney.entity.LearnerJourney;
import com.journey.feature.learnerJourney.repository.LearnerJourneyRepository;
import com.journey.feature.notification.api.NotificationDispatcher;
import com.journey.feature.notification.api.NotificationRequest;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * Turns exam events into notifications.
 *
 * <p>The exam service knows about attempts and answers; it does not know who a learner is or who
 * assigned them their journey. Rather than push those concerns into {@code ExamService}, the events
 * stay thin and this listener resolves the people involved. A listener is glue, and glue is the
 * right place for a query that spans features — which is also why it reads repositories directly
 * instead of calling {@code LearnerJourneyService}, whose service already depends on
 * {@code ExamService} and would make the cycle real.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamNotifier {

    private static final String EXAM_SUBMITTED = "exam-submitted";
    private static final String EXAM_GRADED = "exam-graded";

    private final NotificationDispatcher notifications;
    private final LearnerJourneyRepository learnerJourneyRepository;
    private final JourneyRepository journeyRepository;
    private final UserRepository userRepository;

    /** The reviewer has to act on this one, so it goes to whoever assigned the journey. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExamSubmitted(ExamSubmittedEvent event) {
        context(event.learnerJourneyId()).ifPresent(context ->
                notifications.dispatch(NotificationRequest.forTemplate(EXAM_SUBMITTED)
                        .party("assigner", context.learnerJourney().getAssignedById())
                        .party("learner", context.learnerJourney().getLearnerId())
                        .variable("learnerName", context.learnerName())
                        .variable("journeyTitle", context.journeyTitle())
                        .variable("learnerJourneyId", event.learnerJourneyId())
                        .build()));
    }

    /** The result is what the learner has been waiting for. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExamGraded(ExamGradedEvent event) {
        context(event.learnerJourneyId()).ifPresent(context ->
                notifications.dispatch(NotificationRequest.forTemplate(EXAM_GRADED)
                        .party("learner", context.learnerJourney().getLearnerId())
                        .party("assigner", context.learnerJourney().getAssignedById())
                        .variable("journeyTitle", context.journeyTitle())
                        .variable("graderName", event.graderName())
                        .variable("scorePercent", event.scorePercent())
                        .variable("outcomeEn", event.passed() ? "Passed" : "Not passed")
                        .variable("outcomeAr", event.passed() ? "ناجح" : "غير ناجح")
                        .variable("learnerJourneyId", event.learnerJourneyId())
                        .build()));
    }

    /**
     * Everything both templates need. Returns empty — with a warning rather than an exception — if
     * the journey went away between the event and this listener; a missing notification is not
     * worth failing anything over.
     */
    private Optional<Context> context(String learnerJourneyId) {
        Optional<LearnerJourney> found = learnerJourneyRepository.findById(learnerJourneyId);
        if (found.isEmpty()) {
            log.warn("No learner journey {} — exam notification skipped.", learnerJourneyId);
            return Optional.empty();
        }
        LearnerJourney lj = found.get();

        String journeyTitle = journeyRepository.findById(lj.getJourneyId())
                .map(journey -> journey.getTitle())
                .orElse("");
        String learnerName = userRepository.findById(lj.getLearnerId())
                .map(user -> user.getName())
                .orElse("");

        return Optional.of(new Context(lj, journeyTitle, learnerName));
    }

    private record Context(LearnerJourney learnerJourney, String journeyTitle, String learnerName) {}
}
