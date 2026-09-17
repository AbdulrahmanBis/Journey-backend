package com.journey.feature.learnerJourney.repository;

import com.journey.feature.learnerJourney.entity.LearnerJourneyUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LearnerJourneyUnitRepository extends JpaRepository<LearnerJourneyUnit, String> {

    List<LearnerJourneyUnit> findByLearnerJourneyId(String learnerJourneyId);

    List<LearnerJourneyUnit> findByLearnerJourneyIdIn(Collection<String> learnerJourneyIds);
}
