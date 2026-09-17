package com.journey.feature.certificate.service;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.enums.CatalogItemType;
import com.journey.common.enums.ItemStatus;
import com.journey.common.security.AccessPolicy;
import com.journey.common.service.IdGeneratorService;
import com.journey.feature.certificate.dto.CertificateDto;
import com.journey.feature.certificate.entity.Certificate;
import com.journey.feature.certificate.event.CertificateIssuedEvent;
import com.journey.feature.certificate.repository.CertificateRepository;
import com.journey.feature.department.dto.DepartmentDto;
import com.journey.feature.department.repository.DepartmentRepository;
import com.journey.feature.exam.dto.ExamAttemptDto;
import com.journey.feature.exam.service.ExamService;
import com.journey.feature.journey.repository.JourneyRepository;
import com.journey.feature.journeyPackage.entity.PackageAssignment;
import com.journey.feature.journeyPackage.entity.PackageAssignmentJourney;
import com.journey.feature.journeyPackage.repository.JourneyPackageRepository;
import com.journey.feature.journeyPackage.repository.PackageAssignmentJourneyRepository;
import com.journey.feature.journeyPackage.repository.PackageAssignmentRepository;
import com.journey.feature.learnerJourney.entity.LearnerJourney;
import com.journey.feature.learnerJourney.entity.LearnerJourneyItem;
import com.journey.feature.learnerJourney.repository.LearnerJourneyItemRepository;
import com.journey.feature.learnerJourney.repository.LearnerJourneyRepository;
import com.journey.feature.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Issues and removes certificates as completion changes, and serves them.
 *
 * <ul>
 *   <li><b>Journey</b> — earned when the learner journey is completed and, if the journey has an exam, the exam
 *       was passed.</li>
 *   <li><b>Package</b> — earned when the assignment is not cancelled and every journey in it that was not
 *       cancelled is completed.</li>
 * </ul>
 * If a completion is undone (a manager reopens a journey), its certificate is removed; completing again issues
 * a new one. Only the learner and staff who can see the learner can read a certificate.
 */
@Service
@RequiredArgsConstructor
public class CertificateService {

    /** No 0/O or 1/I/L, so a code reads back unambiguously. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CertificateRepository certificates;
    private final LearnerJourneyRepository learnerJourneys;
    private final LearnerJourneyItemRepository learnerJourneyItems;
    private final JourneyRepository journeys;
    private final ExamService exams;
    private final PackageAssignmentRepository packageAssignments;
    private final PackageAssignmentJourneyRepository packageJourneys;
    private final JourneyPackageRepository packages;
    private final DepartmentRepository departments;
    private final IdGeneratorService idGenerator;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;

    // ─── Keeping certificates in step ───────────────────────────────────────────────────

    /** The journey's own certificate, and those of any packages it belongs to. */
    @Transactional
    public void syncForLearnerJourney(String learnerJourneyId) {
        learnerJourneys.findById(learnerJourneyId).ifPresent(lj -> {
            syncJourney(lj);
            for (PackageAssignmentJourney link : packageJourneys.findByLearnerJourneyId(learnerJourneyId)) {
                syncForPackageAssignment(link.getPackageAssignmentId());
            }
        });
    }

    @Transactional
    public void syncForPackageAssignment(String packageAssignmentId) {
        packageAssignments.findById(packageAssignmentId).ifPresent(this::syncPackage);
    }

    private void syncJourney(LearnerJourney lj) {
        Optional<Certificate> existing = certificates.findByLearnerJourneyId(lj.getId());
        boolean completed = lj.getStatus() == ItemStatus.COMPLETED.getCode();
        boolean hasExam = completed && exams.getExam(lj.getJourneyId()) != null;
        ExamAttemptDto attempt = hasExam ? exams.getAttempt(lj.getId()) : null;
        boolean earned = completed && (!hasExam || (attempt != null && Boolean.TRUE.equals(attempt.passed())));

        if (earned && existing.isEmpty()) {
            String title = journeys.findById(lj.getJourneyId()).map(j -> j.getTitle()).orElse("");
            Certificate issued = certificates.save(Certificate.builder()
                    .id(idGenerator.next(IdGeneratorService.CERTIFICATE, "cert-"))
                    .code(newCode("JRN"))
                    .type(CatalogItemType.JOURNEY.getCode())
                    .learnerId(lj.getLearnerId())
                    .learnerJourneyId(lj.getId())
                    .title(title)
                    .examScorePercent(attempt == null ? null : attempt.scorePercent())
                    .completedAt(lj.getCompletedAt() != null ? lj.getCompletedAt() : LocalDateTime.now())
                    .build());
            events.publishEvent(new CertificateIssuedEvent(issued.getId(), issued.getLearnerId(), title, false));
        } else if (!earned && existing.isPresent()) {
            certificates.delete(existing.get());
        }
    }

    private void syncPackage(PackageAssignment pa) {
        Optional<Certificate> existing = certificates.findByPackageAssignmentId(pa.getId());
        List<LearnerJourney> live = packageJourneys.findByPackageAssignmentIdOrderByPosition(pa.getId()).stream()
                .map(link -> learnerJourneys.findById(link.getLearnerJourneyId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(lj -> lj.getStatus() != ItemStatus.CANCELLED.getCode())
                .toList();
        boolean earned = pa.getCancelledAt() == null && !live.isEmpty()
                && live.stream().allMatch(lj -> lj.getStatus() == ItemStatus.COMPLETED.getCode());

        if (earned && existing.isEmpty()) {
            String title = packages.findById(pa.getPackageId()).map(p -> p.getTitle()).orElse("");
            LocalDateTime completedAt = live.stream().map(LearnerJourney::getCompletedAt).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(LocalDateTime.now());
            Certificate issued = certificates.save(Certificate.builder()
                    .id(idGenerator.next(IdGeneratorService.CERTIFICATE, "cert-"))
                    .code(newCode("PKG"))
                    .type(CatalogItemType.PACKAGE.getCode())
                    .learnerId(pa.getLearnerId())
                    .packageAssignmentId(pa.getId())
                    .title(title)
                    .completedAt(completedAt)
                    .build());
            events.publishEvent(new CertificateIssuedEvent(issued.getId(), issued.getLearnerId(), title, true));
        } else if (!earned && existing.isPresent()) {
            certificates.delete(existing.get());
        }
    }

    /** e.g. JRN-7K2F-9QXA. */
    private String newCode(String prefix) {
        String code;
        do {
            StringBuilder sb = new StringBuilder(prefix);
            for (int i = 0; i < 8; i++) {
                if (i % 4 == 0) sb.append("-");
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (certificates.existsByCode(code));
        return code;
    }

    // ─── Reading ────────────────────────────────────────────────────────────────────────

    /** A learner's certificates, newest first: the caller's own, or a learner the caller can see. */
    @Transactional(readOnly = true)
    public List<CertificateDto> listForLearner(String learnerId) {
        User actor = access.actor();
        User learner = learnerId == null || learnerId.isBlank() || learnerId.equals(actor.getId())
                ? actor : access.requireViewable(learnerId);
        return certificates.findByLearnerIdOrderByCompletedAtDesc(learner.getId()).stream()
                .map(c -> toDto(c, learner)).toList();
    }

    @Transactional(readOnly = true)
    public CertificateDto get(String id) {
        Certificate certificate = certificates.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.CERTIFICATE_NOT_FOUND));
        User learner = access.requireViewable(certificate.getLearnerId());
        return toDto(certificate, learner);
    }

    private CertificateDto toDto(Certificate c, User learner) {
        DepartmentDto department = departments.findById(learner.getDepartmentId())
                .map(d -> new DepartmentDto(d.getId(), d.getNameEn(), d.getNameAr(), null)).orElse(null);
        boolean isPackage = c.getType() == CatalogItemType.PACKAGE.getCode();

        List<LearnerJourney> covered;
        String reviewer;
        List<String> journeyTitles = null;
        if (isPackage) {
            covered = packageJourneys.findByPackageAssignmentIdOrderByPosition(c.getPackageAssignmentId()).stream()
                    .map(link -> learnerJourneys.findById(link.getLearnerJourneyId()).orElse(null))
                    .filter(Objects::nonNull)
                    .filter(lj -> lj.getStatus() == ItemStatus.COMPLETED.getCode())
                    .toList();
            journeyTitles = covered.stream()
                    .map(lj -> journeys.findById(lj.getJourneyId()).map(j -> j.getTitle()).orElse(""))
                    .toList();
            reviewer = packageAssignments.findById(c.getPackageAssignmentId())
                    .map(PackageAssignment::getAssignedByName).orElse(null);
        } else {
            covered = learnerJourneys.findById(c.getLearnerJourneyId()).map(List::of).orElse(List.of());
            reviewer = covered.isEmpty() ? null : covered.get(0).getAssignedByName();
        }
        double hours = covered.isEmpty() ? 0 : learnerJourneyItems
                .findByLearnerJourneyIdIn(covered.stream().map(LearnerJourney::getId).toList()).stream()
                .map(LearnerJourneyItem::getTimeSpentHours).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).sum();

        return new CertificateDto(c.getId(), c.getCode(), CatalogItemType.fromCode(c.getType()).toDto(), c.getTitle(),
                learner.getId(), learner.getName(), department, c.getLearnerJourneyId(), c.getPackageAssignmentId(),
                c.getExamScorePercent(), Math.round(hours * 10) / 10.0, reviewer, journeyTitles,
                c.getCompletedAt(), c.getIssuedAt());
    }
}
