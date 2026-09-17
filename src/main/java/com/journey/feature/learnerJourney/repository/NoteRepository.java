package com.journey.feature.learnerJourney.repository;

import com.journey.feature.learnerJourney.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, String> {

    List<Note> findByLearnerJourneyItemIdOrderByTimestampAsc(String learnerJourneyItemId);

    List<Note> findByLearnerJourneyItemIdIn(java.util.Collection<String> learnerJourneyItemIds);

    List<Note> findByLearnerJourneyUnitIdOrderByTimestampAsc(String learnerJourneyUnitId);

    List<Note> findByLearnerJourneyUnitIdIn(java.util.Collection<String> learnerJourneyUnitIds);
}
