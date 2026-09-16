package com.journey.feature.learnerJourney.repository;

import com.journey.feature.learnerJourney.entity.LearnerJourney;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearnerJourneyRepository extends JpaRepository<LearnerJourney, String> {

    List<LearnerJourney> findByLearnerId(String learnerId);

    /**
     * A learner may hold a journey more than once only if the earlier ones were cancelled, so
     * "status not CANCELLED" matches at most one row.
     */
    Optional<LearnerJourney> findFirstByJourneyIdAndLearnerIdAndStatusNot(String journeyId, String learnerId, Integer status);

    boolean existsByJourneyIdAndLearnerIdAndStatusNot(String journeyId, String learnerId, Integer status);

    /** Learners currently holding a journey, for the catalog's "N learners". */
    long countByJourneyIdAndStatusNot(String journeyId, Integer status);

    Optional<LearnerJourney> findFirstByJourneyIdAndLearnerIdOrderByAssignedAtDesc(String journeyId, String learnerId);
}
