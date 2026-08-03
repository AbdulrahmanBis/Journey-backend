package com.journey.feature.exam.repository;

import com.journey.feature.exam.entity.ExamAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamAnswerRepository extends JpaRepository<ExamAnswer, Long> {

    List<ExamAnswer> findByAttemptId(String attemptId);

    void deleteByAttemptId(String attemptId);
}