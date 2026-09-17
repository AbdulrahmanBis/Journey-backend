package com.journey.feature.announcement.event;

import com.journey.feature.notification.api.NotificationDispatcher;
import com.journey.feature.notification.api.NotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells an announcement's audience about it, one request per person so each gets their own in-app
 * notification and an email in their own language. The link opens it on their dashboard.
 */
@Component
@RequiredArgsConstructor
public class AnnouncementNotifier {

    static final String PUBLISHED = "announcement-published";

    private final NotificationDispatcher notifications;

    /** The in-app route every notification for this announcement points at. */
    public static String linkTo(String announcementId) {
        return "/dashboard?announcement=" + announcementId;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublished(AnnouncementPublishedEvent event) {
        for (String recipientId : event.recipientIds()) {
            notifications.dispatch(NotificationRequest.forTemplate(PUBLISHED)
                    .party("recipient", recipientId)
                    .variable("announcementId", event.announcementId())
                    .variable("announcementTitle", event.title())
                    .variable("excerpt", event.excerpt())
                    .variable("authorName", event.authorName())
                    .build());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeleted(AnnouncementDeletedEvent event) {
        notifications.withdraw(linkTo(event.announcementId()));
    }
}
