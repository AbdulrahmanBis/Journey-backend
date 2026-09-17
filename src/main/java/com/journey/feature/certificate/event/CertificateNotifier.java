package com.journey.feature.certificate.event;

import com.journey.feature.notification.api.NotificationDispatcher;
import com.journey.feature.notification.api.NotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Tells the learner they earned a certificate, with a link to it. */
@Component
@RequiredArgsConstructor
public class CertificateNotifier {

    private final NotificationDispatcher notifications;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssued(CertificateIssuedEvent event) {
        notifications.dispatch(NotificationRequest.forTemplate("certificate-issued")
                .party("learner", event.learnerId())
                .variable("certificateId", event.certificateId())
                .variable("title", event.title())
                .variable("kindEn", event.isPackage() ? "package" : "journey")
                .variable("kindAr", event.isPackage() ? "الحزمة" : "الرحلة")
                .build());
    }
}
