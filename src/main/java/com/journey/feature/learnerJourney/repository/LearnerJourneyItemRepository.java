package com.journey.feature.learnerJourney.repository;

import com.journey.feature.learnerJourney.entity.LearnerJourneyItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearnerJourneyItemRepository extends JpaRepository<LearnerJourneyItem, String> {

    List<LearnerJourneyItem> findByLearnerJourneyId(String learnerJourneyId);

    List<LearnerJourneyItem> findByLearnerJourneyIdIn(java.util.Collection<String> learnerJourneyIds);

    Optional<LearnerJourneyItem> findByJourneyItemIdAndLearnerJourneyId(String journeyItemId,
                                                                         String learnerJourneyId);
}
