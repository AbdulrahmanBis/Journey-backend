package com.journey.feature.journeyPackage.event;

import com.journey.feature.notification.api.NotificationDispatcher;
import com.journey.feature.notification.api.NotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * One notification per package, not one per journey in it: the journeys a package creates are
 * assigned (and cancelled) quietly, and this is the only announcement.
 */
@Component
@RequiredArgsConstructor
public class PackageNotifier {

    private static final String PACKAGE_ASSIGNED = "package-assigned";
    private static final String PACKAGE_CANCELLED = "package-cancelled";

    private final NotificationDispatcher notifications;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPackageAssigned(PackageAssignedEvent event) {
        notifications.dispatch(NotificationRequest.forTemplate(PACKAGE_ASSIGNED)
                .party("learner", event.learnerId())
                .party("assigner", event.assignerId())
                .variable("assignerName", event.assignerName())
                .variable("packageTitle", event.packageTitle())
                .variable("journeyCount", String.valueOf(event.journeyCount()))
                .variable("packageAssignmentId", event.packageAssignmentId())
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPackageCancelled(PackageCancelledEvent event) {
        notifications.dispatch(NotificationRequest.forTemplate(PACKAGE_CANCELLED)
                .party("learner", event.learnerId())
                .variable("packageTitle", event.packageTitle())
                .variable("packageAssignmentId", event.packageAssignmentId())
                .build());
    }
}
