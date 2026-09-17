package com.journey.feature.exam.repository;

import com.journey.feature.exam.entity.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, String> {

    Optional<ExamAttempt> findByLearnerJourneyId(String learnerJourneyId);

    java.util.List<ExamAttempt> findByLearnerJourneyIdIn(java.util.Collection<String> learnerJourneyIds);
}