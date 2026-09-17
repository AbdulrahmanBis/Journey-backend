package com.journey.common.enums;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.dto.EnumValueDto;
import lombok.Getter;

/**
 * The kind of question on an exam.
 *
 * <p>Previously this enum held grading statuses (Submitted/Pending/Failed/Passed), which duplicated
 * {@code ExamStatus} and had nothing to do with question types — nothing referenced it. It now holds
 * the three real question types, matching what the frontend renders.
 *
 * <p>Stored in {@code exam_questions.question_type} as the {@code code}.
 */
@Getter
public enum ExamQuestionType {

    MULTIPLE_CHOICE(1001, "Multiple choice", "اختيار من متعدد"),
    YES_NO(1002, "Yes / No", "نعم / لا"),
    OPEN(1003, "Open question", "سؤال مفتوح");

    private final int code;
    private final String english;
    private final String arabic;

    ExamQuestionType(int code, String english, String arabic) {
        this.code = code;
        this.english = english;
        this.arabic = arabic;
    }

    public EnumValueDto toDto() {
        return new EnumValueDto(code, english, arabic);
    }

    public static ExamQuestionType fromCode(int code) {
        for (ExamQuestionType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new ApiException(ErrorCode.UNKNOWN_CODE, code);
    }

    public static ExamQuestionType fromEnglish(String english) {
        for (ExamQuestionType type : values()) {
            if (type.english.equalsIgnoreCase(english)) {
                return type;
            }
        }
        throw new ApiException(ErrorCode.UNKNOWN_CODE, english);
    }

    public static ExamQuestionType fromArabic(String arabic) {
        for (ExamQuestionType type : values()) {
            if (type.arabic.equals(arabic)) {
                return type;
            }
        }
        throw new ApiException(ErrorCode.UNKNOWN_CODE, arabic);
    }
}
