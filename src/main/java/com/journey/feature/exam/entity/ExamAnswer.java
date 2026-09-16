package com.journey.feature.exam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exam_answers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false, length = 36)
    private String attemptId;

    @Column(name = "question_id", nullable = false, length = 36)
    private String questionId;

    @Column(name = "selected_option_index")
    private Integer selectedOptionIndex;

    @Column(name = "bool_answer")
    private Boolean boolAnswer;

    @Column(name = "open_text", columnDefinition = "TEXT")
    private String openText;

    @Column(name = "marked_correct")
    private Boolean markedCorrect;
}