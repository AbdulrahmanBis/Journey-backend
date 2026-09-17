package com.journey.feature.journey.repository;

import com.journey.feature.journey.entity.JourneyItem;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JourneyItemRepository extends JpaRepository<JourneyItem, String> {

    List<JourneyItem> findByJourneyIdOrderByOrder(String journeyId);

    long countByJourneyId(String journeyId);

    List<JourneyItem> findByJourneyIdIn(java.util.Collection<String> journeyIds);

    @Modifying
    @Transactional
    void deleteByJourneyId(String journeyId);
}
