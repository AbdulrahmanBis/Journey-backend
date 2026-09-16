package com.journey.feature.journeyPackage.repository;

import com.journey.feature.journeyPackage.entity.PackageAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PackageAssignmentRepository extends JpaRepository<PackageAssignment, String> {

    List<PackageAssignment> findByLearnerIdOrderByAssignedAtDesc(String learnerId);

    List<PackageAssignment> findByIdIn(Collection<String> ids);

    boolean existsByPackageIdAndLearnerIdAndCancelledAtIsNull(String packageId, String learnerId);

    Optional<PackageAssignment> findFirstByPackageIdAndLearnerIdAndCancelledAtIsNull(String packageId, String learnerId);

    Optional<PackageAssignment> findFirstByPackageIdAndLearnerIdOrderByAssignedAtDesc(String packageId, String learnerId);

    long countByPackageId(String packageId);

    long countByPackageIdAndCancelledAtIsNull(String packageId);
}
