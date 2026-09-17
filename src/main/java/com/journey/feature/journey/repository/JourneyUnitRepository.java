package com.journey.feature.journey.repository;

import com.journey.feature.journey.entity.JourneyUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JourneyUnitRepository extends JpaRepository<JourneyUnit, String> {

    List<JourneyUnit> findByJourneyIdOrderByOrder(String journeyId);

    List<JourneyUnit> findByJourneyIdInOrderByOrder(java.util.Collection<String> journeyIds);
}
