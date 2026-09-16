package com.journey.feature.journeyPackage.repository;

import com.journey.feature.journeyPackage.entity.JourneyPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JourneyPackageRepository extends JpaRepository<JourneyPackage, String> {

    List<JourneyPackage> findAllByOrderByTitleAsc();
}
