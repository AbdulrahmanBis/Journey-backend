package com.journey.feature.exam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exam_questions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamQuestion {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "exam_id", nullable = false, length = 36)
    private String examId;

    @Column(name = "question_order")
    private Integer questionOrder;

    @Column(name = "question_type", nullable = false)
    private Integer questionType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String prompt;

    @Column(name = "option_1")
    private String option1;

    @Column(name = "option_2")
    private String option2;

    @Column(name = "option_3")
    private String option3;

    @Column(name = "option_4")
    private String option4;

    @Column(name = "correct_option_index")
    private Integer correctOptionIndex;

    @Column(name = "correct_bool_answer")
    private Boolean correctBoolAnswer;
}