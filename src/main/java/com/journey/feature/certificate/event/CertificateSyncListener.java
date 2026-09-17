package com.journey.feature.certificate.event;

import com.journey.feature.certificate.service.CertificateService;
import com.journey.feature.journeyPackage.event.PackageCancelledEvent;
import com.journey.feature.learnerJourney.event.JourneyStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps certificates in step with completion. Runs synchronously inside the transaction that changed the
 * status, so a certificate is never issued for work that then rolled back.
 */
@Component
@RequiredArgsConstructor
public class CertificateSyncListener {

    private final CertificateService certificates;

    @EventListener
    public void onJourneyStatusChanged(JourneyStatusChangedEvent event) {
        certificates.syncForLearnerJourney(event.learnerJourneyId());
    }

    @EventListener
    public void onPackageCancelled(PackageCancelledEvent event) {
        certificates.syncForPackageAssignment(event.packageAssignmentId());
    }
}
