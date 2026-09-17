package com.journey.common.enums;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.dto.EnumValueDto;
import lombok.Getter;

@Getter
public enum ExamAttemptStatus {

    DRAFT(1001, "draft", "مسودة"),
    SUBMITTED(1002, "submitted", "تم الإرسال"),
    GRADED(1003, "graded", "تم التصحيح");

    private final int code;
    private final String english;
    private final String arabic;

    ExamAttemptStatus(int code, String english, String arabic) {
        this.code = code;
        this.english = english;
        this.arabic = arabic;
    }

    public static ExamAttemptStatus fromCode(int code) {
        for (ExamAttemptStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new ApiException(ErrorCode.UNKNOWN_CODE, code);
    }

    public static ExamAttemptStatus fromEnglish(String english) {
        for (ExamAttemptStatus status : values()) {
            if (status.english.equalsIgnoreCase(english)) {
                return status;
            }
        }
        throw new ApiException(ErrorCode.UNKNOWN_CODE, english);
    }

    public static ExamAttemptStatus fromArabic(String arabic) {
        for (ExamAttemptStatus status : values()) {
            if (status.arabic.equals(arabic)) {
                return status;
            }
        }
        throw new ApiException(ErrorCode.UNKNOWN_CODE, arabic);
    }

    public EnumValueDto toDto() {
        return new EnumValueDto(code, english, arabic);
    }
}