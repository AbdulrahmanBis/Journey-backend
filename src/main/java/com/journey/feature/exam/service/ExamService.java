package com.journey.feature.exam.service;


import com.journey.common.enums.ExamAttemptStatus;
import com.journey.common.enums.ExamQuestionType;
import com.journey.common.service.IdGeneratorService;
import com.journey.feature.exam.dto.*;
import com.journey.feature.exam.entity.*;
import com.journey.feature.exam.repository.*;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamAnswerRepository examAnswerRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final IdGeneratorService idGenerator;

    /** Option choices are 1001-based codes, matching every other enum-ish value in the system. */
    public static final int OPTION_CODE_BASE = 1001;
    private static final int MAX_OPTIONS = 4;


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

    /**
     * Creates the journey's exam, or updates it in place when one already exists.
     *
     * <p>The previous implementation deleted the exam row and inserted a new one, which cascaded
     * away every learner's attempt and answers on each edit. Updating in place keeps that history.
     */
    @Transactional
    public ExamDto saveExam(String journeyId, SaveExamRequest req) {

        Exam exam = examRepository.findByJourneyId(journeyId).orElse(null);

        if (exam == null) {
            exam = Exam.builder()
                    .id(idGenerator.next(IdGeneratorService.EXAM, "ex-"))
                    .journeyId(journeyId)
                    .title(req.title())
                    .passingScorePercent(req.passingScorePercent())
                    .createdById(req.createdById())
                    .createdByName(req.createdByName())
                    .updatedAt(LocalDateTime.now())
                    .build();
        } else {
            exam.setTitle(req.title());
            exam.setPassingScorePercent(req.passingScorePercent());
            exam.setUpdatedAt(LocalDateTime.now());
        }

        exam = examRepository.save(exam);

        /*
         * Reconcile questions instead of deleting them all and re-inserting.
         *
         * exam_answers.question_id cascades on delete, so wiping every question on each save also
         * wiped the learners' submitted answers — editing one typo in an exam destroyed attempt
         * history. Questions the client sends back by id are updated in place, so their answers
         * survive; only questions the client actually dropped are deleted.
         */
        List<ExamQuestion> existing = examQuestionRepository.findByExamIdOrderByQuestionOrder(exam.getId());
        Map<String, ExamQuestion> existingById = existing.stream()
                .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));

        Set<String> keptIds = req.questions().stream()
                .map(QuestionDraftDto::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (ExamQuestion dropped : existing) {
            if (!keptIds.contains(dropped.getId())) {
                examQuestionRepository.delete(dropped);
            }
        }

        int order = 1;

        for (QuestionDraftDto q : req.questions()) {

            ExamQuestion question = q.id() != null ? existingById.get(q.id()) : null;

            if (question == null) {
                question = new ExamQuestion();
                question.setId(q.id() != null
                        ? q.id()
                        : idGenerator.next(IdGeneratorService.EXAM_QUESTION, "eq-"));
            }

            question.setExamId(exam.getId());
            question.setQuestionOrder(order++);
            question.setQuestionType(resolveQuestionType(q.type()).getCode());
            question.setPrompt(q.prompt());
            question.setOption1(q.options() != null && q.options().size() > 0 ? q.options().get(0) : null);
            question.setOption2(q.options() != null && q.options().size() > 1 ? q.options().get(1) : null);
            question.setOption3(q.options() != null && q.options().size() > 2 ? q.options().get(2) : null);
            question.setOption4(q.options() != null && q.options().size() > 3 ? q.options().get(3) : null);
            question.setCorrectOptionIndex(validOptionCode(q.correctOptionIndex()));
            question.setCorrectBoolAnswer(q.correctBoolAnswer());

            examQuestionRepository.save(question);
        }

        return getExam(journeyId);
    }

    @Transactional
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
                ExamQuestionType.fromCode(q.getQuestionType()).toDto(),
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
                ExamAttemptStatus.fromCode(attempt.getStatus()).toDto(),
                answers,
                attempt.getSubmittedAt(),
                attempt.getGradedAt(),
                attempt.getGradedById(),
                attempt.getGradedByName(),
                attempt.getScorePercent(),
                attempt.getPassed()
        );
    }

    @Transactional
    public ExamAttemptDto submitAttempt(String learnerJourneyId,
                                        SubmitAttemptRequest req) {

        Exam exam = examRepository.findById(req.examId())
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found."));

        // One attempt per learner-journey — the contract promises 409 on a repeat submit.
        if (examAttemptRepository.findByLearnerJourneyId(learnerJourneyId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This exam has already been submitted.");
        }

        ExamAttempt attempt = ExamAttempt.builder()
                .id(idGenerator.next(IdGeneratorService.EXAM_ATTEMPT, "att-"))
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

            if (q == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Answer refers to a question that is not on this exam: " + dto.questionId());
            }

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
                            .selectedOptionIndex(validOptionCode(dto.selectedOptionIndex()))
                            .boolAnswer(dto.boolAnswer())
                            .openText(dto.openText())
                            .markedCorrect(markedCorrect)
                            .build()
            );
        }

        return getAttempt(learnerJourneyId);
    }

    @Transactional
    public ExamAttemptDto gradeAttempt(String attemptId,
                                       GradeAttemptRequest req) {

        ExamAttempt attempt = examAttemptRepository.findById(attemptId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found."));

        List<ExamAnswer> answers =
                examAnswerRepository.findByAttemptId(attemptId);

        if (answers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This attempt has no answers to grade.");
        }

        Map<String, QuestionMarkDto> marks =
                req.marks() == null
                        ? Map.of()
                        : req.marks()
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

        int score = (int) ((correct * 100.0) / answers.size());

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

    /**
     * Option choices travel as 1001-based codes (1001 = first option … 1004 = fourth).
     * Rejects anything outside that range so a bad client can't write junk into the column.
     */
    private Integer validOptionCode(Integer code) {
        if (code == null) return null;
        if (code < OPTION_CODE_BASE || code >= OPTION_CODE_BASE + MAX_OPTIONS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Option code must be between " + OPTION_CODE_BASE + " and "
                            + (OPTION_CODE_BASE + MAX_OPTIONS - 1) + ", got: " + code);
        }
        return code;
    }

    /** Turns an inbound question-type code into the enum, answering 400 rather than 500. */
    private ExamQuestionType resolveQuestionType(Integer code) {
        try {
            return ExamQuestionType.fromCode(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown question type code: " + code);
        }
    }
}
