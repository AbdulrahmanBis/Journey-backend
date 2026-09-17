package com.journey.feature.certificate.repository;

import com.journey.feature.certificate.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, String> {

    List<Certificate> findByLearnerIdOrderByCompletedAtDesc(String learnerId);

    Optional<Certificate> findByLearnerJourneyId(String learnerJourneyId);

    Optional<Certificate> findByPackageAssignmentId(String packageAssignmentId);

    boolean existsByCode(String code);
}
