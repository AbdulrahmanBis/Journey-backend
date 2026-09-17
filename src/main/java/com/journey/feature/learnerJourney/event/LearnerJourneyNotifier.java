package com.journey.feature.learnerJourney.event;

import com.journey.feature.notification.api.NotificationDispatcher;
import com.journey.feature.notification.api.NotificationRequest;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Translates learner-journey events into notification requests.
 *
 * <p>This class is the whole of the coupling between journeys and notifications, and it points one
 * way: of the notification module it imports only the {@code api} package, and the notification
 * module imports nothing from here. That is what keeps the module liftable into its own service.
 * (It also reads {@code UserRepository}, purely to turn an id into a display name — a listener is
 * glue, and a query spanning features belongs in glue rather than in either service.)
 *
 * <p>{@link TransactionPhase#AFTER_COMMIT} matters. On {@code BEFORE_COMMIT} — or with a plain
 * {@code @EventListener} — a later failure could roll the work back after someone had already been
 * told about it. Nothing is announced until it is durable.
 *
 * <p><b>Routing lives here, not in the templates.</b> A template's {@code config.json} chooses
 * recipients by <em>party name</em>, which is static. Where the right recipient depends on who
 * acted — a note goes to whoever did not write it — this class works out who that is and supplies
 * them under a fixed name ({@code counterpart}). The config stays declarative; the conditional
 * logic stays in code, where it can be read.
 */
@Component
@RequiredArgsConstructor
public class LearnerJourneyNotifier {

    private static final String JOURNEY_ASSIGNED = "journey-assigned";
    private static final String NOTE_ADDED = "note-added";
    private static final String ITEM_STATUS_CHANGED = "item-status-changed";
    private static final String JOURNEY_COMPLETED = "journey-completed";
    private static final String JOURNEY_CANCELLED = "journey-cancelled";
    private static final String JOURNEY_DUE_SOON = "journey-due-soon";
    private static final String UNIT_SUBMITTED = "unit-submitted";
    private static final String UNIT_STATUS_CHANGED = "unit-status-changed";
    private static final String JOURNEY_OVERDUE = "journey-overdue";

    private final NotificationDispatcher notifications;

    /** Display names only. Events carry ids; turning an id into a name is this listener's job. */
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJourneyAssigned(JourneyAssignedEvent event) {
        notifications.dispatch(NotificationRequest.forTemplate(JOURNEY_ASSIGNED)
                .party("learner", event.learnerId())
                .party("assigner", event.assignerId())
                .variable("assignerName", event.assignerName())
                .variable("journeyTitle", event.journeyTitle())
                .variable("learnerJourneyId", event.learnerJourneyId())
                .build());
    }

    /**
     * Notes are a conversation, so the notification goes to the other side of it. Notifying the
     * author of their own note would be noise, and it is the unanswered note that actually needs
     * chasing.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoteAdded(NoteAddedEvent event) {
        String counterpart = event.authorIsLearner() ? event.assignerId() : event.learnerId();
        if (counterpart == null || counterpart.equals(event.authorId())) {
            return;
        }

        notifications.dispatch(NotificationRequest.forTemplate(NOTE_ADDED)
                .party("counterpart", counterpart)
                .variable("authorName", event.authorName())
                .variable("journeyTitle", event.journeyTitle())
                .variable("itemTitle", event.itemTitle())
                .variable("learnerJourneyId", event.learnerJourneyId())
                .build());
    }

    /**
     * Only when someone <em>else</em> moved the learner's item. A learner ticking off their own
     * work does not need to be told they did it, and that is the common case — notifying on it
     * would make the bell useless.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onItemStatusChanged(ItemStatusChangedEvent event) {
        if (event.actorId() == null || event.actorId().equals(event.learnerId())) {
            return;
        }

        notifications.dispatch(NotificationRequest.forTemplate(ITEM_STATUS_CHANGED)
                .party("learner", event.learnerId())
                .variable("actorName", nameOf(event.actorId()))
                .variable("journeyTitle", event.journeyTitle())
                .variable("itemTitle", event.itemTitle())
                .variable("statusEn", event.statusEnglish())
                .variable("statusAr", event.statusArabic())
                .variable("learnerJourneyId", event.learnerJourneyId())
                .build());
    }

    /** The assigner needs to know, because completion is what makes the exam actionable. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJourneyCompleted(JourneyCompletedEvent event) {
        notifications.dispatch(NotificationRequest.forTemplate(JOURNEY_COMPLETED)
                .party("assigner", event.assignerId())
                .party("learner", event.learnerId())
                .variable("learnerName", nameOf(event.learnerId()))
                .variable("journeyTitle", event.journeyTitle())
                .variable("learnerJourneyId", event.learnerJourneyId())
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJourneyCancelled(JourneyCancelledEvent event) {
        notifications.dispatch(NotificationRequest.forTemplate(JOURNEY_CANCELLED)
                .party("learner", event.learnerId())
                .variable("journeyTitle", event.journeyTitle())
                .variable("learnerJourneyId", event.learnerJourneyId())
                .build());
    }

    /** The reviewer (assigner) has something to look at. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitSubmitted(UnitSubmittedEvent event) {
        notifications.dispatch(NotificationRequest.forTemplate(UNIT_SUBMITTED)
                .party("assigner", event.assignerId())
                .variable("learnerName", nameOf(event.learnerId()))
                .variable("journeyTitle", event.journeyTitle())
                .variable("unitTitle", event.unitTitle())
                .variable("learnerJourneyId", event.learnerJourneyId())
                .build());
    }

    /** Only when someone other than the learner moved it, like item changes. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitStatusChanged(UnitStatusChangedEvent event) {
        if (event.actorId() == null || event.actorId().equals(event.learnerId())) return;
        notifications.dispatch(NotificationRequest.forTemplate(UNIT_STATUS_CHANGED)
                .party("learner", event.learnerId())
                .variable("actorName", nameOf(event.actorId()))
                .variable("journeyTitle", event.journeyTitle())
                .variable("unitTitle", event.unitTitle())
                .variable("statusEn", event.statusEnglish())
                .variable("statusAr", event.statusArabic())
                .variable("learnerJourneyId", event.learnerJourneyId())
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJourneyDueSoon(JourneyDueSoonEvent event) {
        notifications.dispatch(NotificationRequest.forTemplate(JOURNEY_DUE_SOON)
                .party("learner", event.learnerId())
                .variable("journeyTitle", event.journeyTitle())
                .variable("dueDate", event.dueDate().toString())
                .variable("daysLeft", String.valueOf(event.daysLeft()))
                .variable("learnerJourneyId", event.learnerJourneyId())
                .build());
    }

    /** The learner, and whoever assigned it (for a self-enrollment, their reviewer). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJourneyOverdue(JourneyOverdueEvent event) {
        notifications.dispatch(NotificationRequest.forTemplate(JOURNEY_OVERDUE)
                .party("learner", event.learnerId())
                .party("assigner", event.assignerId())
                .variable("learnerName", nameOf(event.learnerId()))
                .variable("journeyTitle", event.journeyTitle())
                .variable("dueDate", event.dueDate().toString())
                .variable("learnerJourneyId", event.learnerJourneyId())
                .build());
    }

    private String nameOf(String userId) {
        if (userId == null) return "";
        return userRepository.findById(userId).map(user -> user.getName()).orElse("");
    }
}
