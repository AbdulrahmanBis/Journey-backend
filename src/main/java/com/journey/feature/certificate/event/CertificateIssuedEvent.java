package com.journey.feature.certificate.event;

/** A certificate was just issued; the learner hears about it once the transaction commits. */
public record CertificateIssuedEvent(String certificateId, String learnerId, String title, boolean isPackage) {}
