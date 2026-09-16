package com.journey.feature.journeyPackage.repository;

import com.journey.feature.journeyPackage.entity.PackageAssignmentJourney;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackageAssignmentJourneyRepository
        extends JpaRepository<PackageAssignmentJourney, PackageAssignmentJourney.Key> {

    List<PackageAssignmentJourney> findByPackageAssignmentIdOrderByPosition(String packageAssignmentId);

    List<PackageAssignmentJourney> findByLearnerJourneyId(String learnerJourneyId);
}
