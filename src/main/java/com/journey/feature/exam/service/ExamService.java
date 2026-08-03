package com.journey.feature.exam.service;



import com.journey.feature.exam.dto.*;
import com.journey.feature.exam.entity.*;
import com.journey.feature.exam.repository.*;
import com.journey.common.enums.ExamAttemptStatus;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamAnswerRepository examAnswerRepository;
    private final ExamAttemptRepository examAttemptRepository;


    @Transactional
    public ExamDto getExam(String journeyId) {

        Exam exam = examRepository.findByJourneyId(journeyId).orElse(null);

        if (exam == null) {
            return null;
        }

        List<ExamQuestionDto> questions = examQuestionRepository
                .findByExamIdOrderByQuestionOrder(exam.getId())
                .stream()
                .map(this::toQuestionDto)
                .toList();

        return new ExamDto(
                exam.getId(),
                exam.getJourneyId(),
                exam.getTitle(),
                exam.getPassingScorePercent(),
                exam.getCreatedById(),
                exam.getCreatedByName(),
                exam.getUpdatedAt(),
                questions
        );
    }

    @Transactional
    public ExamDto saveExam(String journeyId, SaveExamRequest req) {

        examRepository.findByJourneyId(journeyId).ifPresent(existing -> {
            examQuestionRepository.deleteByExamId(existing.getId());
            examRepository.delete(existing);
        });

        Exam exam = Exam.builder()
                .journeyId(journeyId)
                .title(req.title())
                .passingScorePercent(req.passingScorePercent())
                .createdById(req.createdById())
                .createdByName(req.createdByName())
                .updatedAt(LocalDateTime.now())
                .build();

        exam = examRepository.save(exam);

        int order = 1;

        for (QuestionDraftDto q : req.questions()) {

            ExamQuestion question = ExamQuestion.builder()
                    .examId(exam.getId())
                    .questionOrder(order++)
                    .questionType(q.type())
                    .prompt(q.prompt())
                    .option1(q.options() != null && q.options().size() > 0 ? q.options().get(0) : null)
                    .option2(q.options() != null && q.options().size() > 1 ? q.options().get(1) : null)
                    .option3(q.options() != null && q.options().size() > 2 ? q.options().get(2) : null)
                    .option4(q.options() != null && q.options().size() > 3 ? q.options().get(3) : null)
                    .correctOptionIndex(q.correctOptionIndex())
                    .correctBoolAnswer(q.correctBoolAnswer())
                    .build();

            examQuestionRepository.save(question);
        }

        return getExam(journeyId);
    }

    public void deleteExam(String journeyId) {

        Exam exam = examRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Exam not found."
                ));

        examQuestionRepository.deleteByExamId(exam.getId());

        examRepository.delete(exam);
    }
    public ExamQuestionDto toQuestionDto(ExamQuestion q) {

        List<String> options = new ArrayList<>();

        if (q.getOption1() != null) options.add(q.getOption1());
        if (q.getOption2() != null) options.add(q.getOption2());
        if (q.getOption3() != null) options.add(q.getOption3());
        if (q.getOption4() != null) options.add(q.getOption4());

        return new ExamQuestionDto(
                q.getId(),
                q.getQuestionType(),
                q.getPrompt(),
                options,
                q.getCorrectOptionIndex(),
                q.getCorrectBoolAnswer()
        );
    }


    @Transactional
    public ExamAttemptDto getAttempt(String learnerJourneyId) {

        ExamAttempt attempt = examAttemptRepository
                .findByLearnerJourneyId(learnerJourneyId)
                .orElse(null);

        if (attempt == null) {
            return null;
        }

        List<ExamAnswerDto> answers = examAnswerRepository
                .findByAttemptId(attempt.getId())
                .stream()
                .map(this::toAnswerDto)
                .toList();

        return new ExamAttemptDto(
                attempt.getId(),
                attempt.getLearnerJourneyId(),
                attempt.getExamId(),
                attempt.getStatus(),
                answers,
                attempt.getSubmittedAt(),
                attempt.getGradedAt(),
                attempt.getGradedById(),
                attempt.getGradedByName(),
                attempt.getScorePercent(),
                attempt.getPassed()
        );
    }

    public ExamAttemptDto submitAttempt(String learnerJourneyId,
                                        SubmitAttemptRequest req) {

        Exam exam = examRepository.findById(req.examId())
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));

        ExamAttempt attempt = ExamAttempt.builder()
                .learnerJourneyId(learnerJourneyId)
                .examId(exam.getId())
                .status(ExamAttemptStatus.SUBMITTED.getCode())
                .submittedAt(LocalDateTime.now())
                .build();

        attempt = examAttemptRepository.save(attempt);

        List<ExamQuestion> questions =
                examQuestionRepository.findByExamIdOrderByQuestionOrder(exam.getId());

        Map<String, ExamQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(
                        ExamQuestion::getId,
                        Function.identity()));

        for (AnswerDraftDto dto : req.answers()) {

            ExamQuestion q = questionMap.get(dto.questionId());

            Boolean markedCorrect = null;

            if (q.getCorrectOptionIndex() != null) {
                markedCorrect =
                        Objects.equals(
                                dto.selectedOptionIndex(),
                                q.getCorrectOptionIndex());
            }

            if (q.getCorrectBoolAnswer() != null) {
                markedCorrect =
                        Objects.equals(
                                dto.boolAnswer(),
                                q.getCorrectBoolAnswer());
            }

            examAnswerRepository.save(
                    ExamAnswer.builder()
                            .attemptId(attempt.getId())
                            .questionId(dto.questionId())
                            .selectedOptionIndex(dto.selectedOptionIndex())
                            .boolAnswer(dto.boolAnswer())
                            .openText(dto.openText())
                            .markedCorrect(markedCorrect)
                            .build()
            );
        }

        return getAttempt(learnerJourneyId);
    }
    public ExamAttemptDto gradeAttempt(String attemptId,
                                       GradeAttemptRequest req) {

        ExamAttempt attempt = examAttemptRepository.findById(attemptId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<ExamAnswer> answers =
                examAnswerRepository.findByAttemptId(attemptId);

        Map<String, QuestionMarkDto> marks =
                req.marks()
                        .stream()
                        .collect(Collectors.toMap(
                                QuestionMarkDto::questionId,
                                Function.identity()));

        int correct = 0;

        for (ExamAnswer answer : answers) {

            if (answer.getMarkedCorrect() == null) {

                QuestionMarkDto mark = marks.get(answer.getQuestionId());

                if (mark != null) {
                    answer.setMarkedCorrect(mark.markedCorrect());
                }
            }

            if (Boolean.TRUE.equals(answer.getMarkedCorrect())) {
                correct++;
            }

            examAnswerRepository.save(answer);
        }

        int score =
                (int) ((correct * 100.0) / answers.size());

        attempt.setStatus(ExamAttemptStatus.GRADED.getCode());
        attempt.setGradedAt(LocalDateTime.now());
        attempt.setGradedById(req.gradedById());
        attempt.setGradedByName(req.gradedByName());
        attempt.setScorePercent(score);
        attempt.setPassed(req.passed());

        examAttemptRepository.save(attempt);

        return getAttempt(attempt.getLearnerJourneyId());
    }
    private ExamAnswerDto toAnswerDto(ExamAnswer answer) {

        return new ExamAnswerDto(
                answer.getQuestionId(),
                answer.getSelectedOptionIndex(),
                answer.getBoolAnswer(),
                answer.getOpenText(),
                answer.getMarkedCorrect()
        );
    }

}
