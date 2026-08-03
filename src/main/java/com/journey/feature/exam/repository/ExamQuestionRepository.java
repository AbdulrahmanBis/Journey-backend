package com.journey.feature.exam.repository;

import com.journey.feature.exam.entity.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, String> {

    List<ExamQuestion> findByExamIdOrderByQuestionOrder(String examId);

    void deleteByExamId(String examId);
}