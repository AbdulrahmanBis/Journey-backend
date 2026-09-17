package com.journey.feature.journey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A question in a unit's quiz. Same shape as an exam question, but only the auto-graded types
 * (multiple choice, yes/no), so the learner gets their result immediately.
 */
@Entity
@Table(name = "unit_quiz_questions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitQuizQuestion {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "unit_id", nullable = false, length = 36)
    private String unitId;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    /** ExamQuestionType code: MULTIPLE_CHOICE or YES_NO. */
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

    /** 1001-based option code, like exam questions. */
    @Column(name = "correct_option_index")
    private Integer correctOptionIndex;

    @Column(name = "correct_bool_answer")
    private Boolean correctBoolAnswer;
}
