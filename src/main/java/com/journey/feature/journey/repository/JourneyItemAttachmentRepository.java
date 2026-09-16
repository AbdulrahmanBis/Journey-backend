package com.journey.feature.journey.repository;

import com.journey.feature.journey.entity.JourneyItemAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JourneyItemAttachmentRepository extends JpaRepository<JourneyItemAttachment, String> {

    List<JourneyItemAttachment> findByJourneyItemIdOrderByOrder(String journeyItemId);

    List<JourneyItemAttachment> findByJourneyItemIdIn(List<String> journeyItemIds);

    void deleteByJourneyItemId(String journeyItemId);
}
