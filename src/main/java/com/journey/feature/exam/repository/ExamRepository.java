package com.journey.feature.exam.repository;

import com.journey.feature.exam.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, String> {

    Optional<Exam> findByJourneyId(String journeyId);

    void deleteByJourneyId(String journeyId);
}