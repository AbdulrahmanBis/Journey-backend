package com.journey.feature.learnerJourney.repository;

import com.journey.feature.learnerJourney.entity.LearnerJourney;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearnerJourneyRepository extends JpaRepository<LearnerJourney, String> {

    List<LearnerJourney> findByLearnerId(String learnerId);

    Optional<LearnerJourney> findByJourneyIdAndLearnerId(String journeyId, String learnerId);

    boolean existsByJourneyIdAndLearnerId(String journeyId, String learnerId);
}
