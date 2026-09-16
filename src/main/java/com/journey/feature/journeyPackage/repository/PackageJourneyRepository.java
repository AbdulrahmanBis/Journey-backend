package com.journey.feature.journeyPackage.repository;

import com.journey.feature.journeyPackage.entity.PackageJourney;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface PackageJourneyRepository extends JpaRepository<PackageJourney, PackageJourney.Key> {

    List<PackageJourney> findByPackageIdOrderByPosition(String packageId);

    @Modifying
    void deleteByPackageId(String packageId);
}
