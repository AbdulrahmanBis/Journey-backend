package com.journey.feature.exam.service;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;

import com.journey.common.enums.ExamAttemptStatus;
import com.journey.common.enums.ExamQuestionType;
import com.journey.common.enums.UserRole;
import com.journey.common.security.AccessPolicy;
import com.journey.common.service.IdGeneratorService;
import com.journey.feature.exam.event.ExamGradedEvent;
import com.journey.feature.exam.event.ExamSubmittedEvent;
import com.journey.feature.exam.dto.*;
import com.journey.feature.exam.entity.*;
import com.journey.feature.exam.repository.*;
import com.journey.feature.learnerJourney.entity.LearnerJourney;
import com.journey.common.enums.ItemStatus;
import com.journey.feature.journey.repository.JourneyRepository;
import com.journey.feature.learnerJourney.repository.LearnerJourneyRepository;
import com.journey.feature.learnerJourney.service.LearnerJourneyService;
import org.springframework.beans.factory.ObjectProvider;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

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
    private final ApplicationEventPublisher events;
    // Repositories rather than LearnerJourneyService, which already depends on this service.
    private final LearnerJourneyRepository learnerJourneyRepository;
    private final UserRepository userRepository;
    private final AccessPolicy access;
    private final JourneyRepository journeyRepository;
    // LearnerJourneyService depends on this service, so it is looked up lazily.
    private final ObjectProvider<LearnerJourneyService> learnerJourneys;

    /** Option choices are 1001-based codes, matching every other enum-ish value in the system. */
    public static final int OPTION_CODE_BASE = 1001;
    private static final int MAX_OPTIONS = 4;


    /**
     * The exam endpoint includes the answer key, so it is for staff only. Learners get their questions,
     * without answers, through their own learner journey.
     */
    public ExamDto getExamForStaff(String journeyId) {
        access.requireRole(AccessPolicy.STAFF);
        return getExam(journeyId);
    }

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
        User author = access.requireRole(AccessPolicy.STAFF);
        if (!journeyRepository.existsById(journeyId)) {
            throw new ApiException(ErrorCode.JOURNEY_NOT_FOUND);
        }
        List<QuestionDraftDto> drafts = req.questions() == null ? List.of()
                : req.questions().stream().filter(Objects::nonNull).toList();
        if (drafts.isEmpty()) {
            throw new ApiException(ErrorCode.EXAM_NEEDS_QUESTION);
        }
        drafts.forEach(ExamService::validateQuestion);

        Exam exam = examRepository.findByJourneyId(journeyId).orElse(null);

        if (exam == null) {
            exam = Exam.builder()
                    .id(idGenerator.next(IdGeneratorService.EXAM, "ex-"))
                    .journeyId(journeyId)
                    .title(req.title())
                    .passingScorePercent(req.passingScorePercent())
                    .createdById(author.getId())
                    .createdByName(author.getName())
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

        Set<String> keptIds = drafts.stream()
                .map(QuestionDraftDto::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (ExamQuestion dropped : existing) {
            if (!keptIds.contains(dropped.getId())) {
                examQuestionRepository.delete(dropped);
            }
        }

        int order = 1;

        for (QuestionDraftDto q : drafts) {

            ExamQuestion question = q.id() != null ? existingById.get(q.id()) : null;

            // An id that is not on this exam is treated as new, never adopted: otherwise it could take over a
            // question that belongs to another exam.
            if (question == null) {
                question = new ExamQuestion();
                question.setId(idGenerator.next(IdGeneratorService.EXAM_QUESTION, "eq-"));
            }

            question.setExamId(exam.getId());
            question.setQuestionOrder(order++);
            question.setQuestionType(resolveQuestionType(q.type()).getCode());
            question.setPrompt(q.prompt().trim());
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
        access.requireRole(AccessPolicy.STAFF);

        Exam exam = examRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new ApiException(ErrorCode.EXAM_NOT_FOUND));

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


    /** GET for the API: the attempt, provided the caller can see this learner. */
    @Transactional
    public ExamAttemptDto getAttemptForCaller(String learnerJourneyId) {
        access.requireViewable(learnerOf(learnerJourneyId).getId());
        return getAttempt(learnerJourneyId);
    }

    /** Internal: no access check — callers such as the journey view have already done theirs. */
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

        // Only the learner sits their own exam.
        if (!learnerOf(learnerJourneyId).getId().equals(access.actor().getId())) {
            throw new ApiException(ErrorCode.LEARNER_ONLY);
        }
        LearnerJourney lj = learnerJourneyRepository.findById(learnerJourneyId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        if (lj.getStatus() == ItemStatus.CANCELLED.getCode()) {
            throw new ApiException(ErrorCode.JOURNEY_CANCELLED);
        }

        // The exam is the one on this learner's journey, whatever examId the client sent.
        Exam exam = examRepository.findByJourneyId(lj.getJourneyId())
                .orElseThrow(() -> new ApiException(ErrorCode.EXAM_NOT_FOUND));
        if (req.examId() != null && !req.examId().equals(exam.getId())) {
            throw new ApiException(ErrorCode.EXAM_NOT_FOUND);
        }
        // Agreed rule: the final exam opens once every unit's items and quiz are done.
        if (!learnerJourneys.getObject().allUnitsReady(lj)) {
            throw new ApiException(ErrorCode.EXAM_LOCKED);
        }

        // One attempt per learner-journey — the contract promises 409 on a repeat submit.
        if (examAttemptRepository.findByLearnerJourneyId(learnerJourneyId).isPresent()) {
            throw new ApiException(ErrorCode.EXAM_ALREADY_SUBMITTED);
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

        List<AnswerDraftDto> answers = req.answers() == null ? List.of()
                : req.answers().stream().filter(Objects::nonNull).toList();
        Set<String> answered = new java.util.HashSet<>();
        for (AnswerDraftDto dto : answers) {
            if (dto.questionId() == null || !questionMap.containsKey(dto.questionId())) {
                throw new ApiException(ErrorCode.EXAM_UNKNOWN_QUESTION);
            }
            if (!answered.add(dto.questionId()) || !isAnswered(questionMap.get(dto.questionId()), dto)) {
                throw new ApiException(ErrorCode.EXAM_ANSWER_ALL);
            }
        }
        if (answered.size() != questions.size()) {
            throw new ApiException(ErrorCode.EXAM_ANSWER_ALL);
        }

        for (AnswerDraftDto dto : answers) {

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
                            .selectedOptionIndex(validOptionCode(dto.selectedOptionIndex()))
                            .boolAnswer(dto.boolAnswer())
                            .openText(dto.openText())
                            .markedCorrect(markedCorrect)
                            .build()
            );
        }

        // The reviewer has to grade this; the listener works out who that is.
        events.publishEvent(new ExamSubmittedEvent(learnerJourneyId, attempt.getId()));

        return getAttempt(learnerJourneyId);
    }

    @Transactional
    public ExamAttemptDto gradeAttempt(String attemptId,
                                       GradeAttemptRequest req) {

        ExamAttempt attempt = examAttemptRepository.findById(attemptId)
                .orElseThrow(() ->
                        new ApiException(ErrorCode.EXAM_ATTEMPT_NOT_FOUND));

        // A reviewer who can see the learner — never the learner grading themselves.
        User grader = access.actor();
        if (!access.isReviewerOf(grader, learnerOf(attempt.getLearnerJourneyId()))) {
            throw new ApiException(ErrorCode.EXAM_GRADE_NOT_ALLOWED);
        }

        List<ExamAnswer> answers =
                examAnswerRepository.findByAttemptId(attemptId);

        if (answers.isEmpty()) {
            throw new ApiException(ErrorCode.EXAM_NOTHING_TO_GRADE);
        }
        if (req.passed() == null) {
            throw new ApiException(ErrorCode.EXAM_RESULT_REQUIRED);
        }

        // The last mark wins when a question is marked twice; marks without a question are ignored.
        Map<String, QuestionMarkDto> marks =
                req.marks() == null
                        ? Map.of()
                        : req.marks()
                        .stream()
                        .filter(m -> m != null && m.questionId() != null)
                        .collect(Collectors.toMap(
                                QuestionMarkDto::questionId,
                                Function.identity(),
                                (first, second) -> second));

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
        attempt.setGradedById(grader.getId());
        attempt.setGradedByName(grader.getName());
        attempt.setScorePercent(score);
        attempt.setPassed(req.passed());

        examAttemptRepository.save(attempt);

        events.publishEvent(new ExamGradedEvent(
                attempt.getLearnerJourneyId(),
                attempt.getId(),
                attempt.getScorePercent(),
                Boolean.TRUE.equals(attempt.getPassed()),
                attempt.getGradedByName()));

        return getAttempt(attempt.getLearnerJourneyId());
    }

    /** A question the author can save: a prompt, and a correct answer that fits its type. */
    private static void validateQuestion(QuestionDraftDto q) {
        if (q.prompt() == null || q.prompt().isBlank()) {
            throw new ApiException(ErrorCode.QUESTION_PROMPT_REQUIRED);
        }
        if (q.type() == null) {
            throw new ApiException(ErrorCode.UNKNOWN_CODE, "null");
        }
        ExamQuestionType type = ExamQuestionType.fromCode(q.type());
        if (type == ExamQuestionType.MULTIPLE_CHOICE) {
            long options = q.options() == null ? 0 : q.options().stream().filter(o -> o != null && !o.isBlank()).count();
            if (options < 2 || options > MAX_OPTIONS || q.options().size() != options) {
                throw new ApiException(ErrorCode.QUESTION_OPTION_COUNT);
            }
            Integer correct = q.correctOptionIndex();
            if (correct == null || correct < OPTION_CODE_BASE || correct >= OPTION_CODE_BASE + options) {
                throw new ApiException(ErrorCode.QUESTION_CORRECT_OPTION, q.prompt().trim());
            }
        } else if (type == ExamQuestionType.YES_NO && q.correctBoolAnswer() == null) {
            throw new ApiException(ErrorCode.QUESTION_CORRECT_YES_NO, q.prompt().trim());
        }
    }

    /** Whether the learner gave an answer of the kind this question takes. */
    private static boolean isAnswered(ExamQuestion q, AnswerDraftDto a) {
        return switch (ExamQuestionType.fromCode(q.getQuestionType())) {
            case MULTIPLE_CHOICE -> a.selectedOptionIndex() != null;
            case YES_NO -> a.boolAnswer() != null;
            case OPEN -> a.openText() != null && !a.openText().isBlank();
        };
    }

    /** The learner a learner-journey belongs to. */
    private User learnerOf(String learnerJourneyId) {
        LearnerJourney lj = learnerJourneyRepository.findById(learnerJourneyId)
                .orElseThrow(() -> new ApiException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        return userRepository.findById(lj.getLearnerId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
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
            throw new ApiException(ErrorCode.EXAM_OPTION_OUT_OF_RANGE);
        }
        return code;
    }

    /** Turns an inbound question-type code into the enum, answering 400 rather than 500. */
    private ExamQuestionType resolveQuestionType(Integer code) {
        try {
            return ExamQuestionType.fromCode(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(ErrorCode.UNKNOWN_CODE, code);
        }
    }
}
