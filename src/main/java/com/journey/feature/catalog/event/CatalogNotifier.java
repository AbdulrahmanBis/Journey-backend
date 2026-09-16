package com.journey.feature.catalog.event;

import com.journey.feature.notification.api.NotificationDispatcher;
import com.journey.feature.notification.api.NotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells the reviewer when a learner enrolls themselves. The learner acted, so they need no
 * notification; the reviewer does, since the work now lands on them.
 */
@Component
@RequiredArgsConstructor
public class CatalogNotifier {

    private static final String SELF_ENROLLED = "self-enrolled";

    private final NotificationDispatcher notifications;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSelfEnrolled(SelfEnrolledEvent event) {
        notifications.dispatch(NotificationRequest.forTemplate(SELF_ENROLLED)
                .party("reviewer", event.reviewerId())
                .variable("learnerName", event.learnerName())
                .variable("title", event.title())
                .variable("typeEn", event.typeEnglish())
                .variable("typeAr", event.typeArabic())
                .variable("targetPath", event.targetPath())
                .build());
    }
}
