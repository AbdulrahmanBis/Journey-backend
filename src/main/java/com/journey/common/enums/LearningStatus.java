package com.journey.common.enums;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.dto.EnumValueDto;
import lombok.Getter;

/**
 * Where the signed-in learner stands with a catalog entry. Coarser than {@link ItemStatus} on
 * purpose: a library shelf only needs "haven't started / on it / done / dropped".
 */
@Getter
public enum LearningStatus {

    NOT_STARTED(1001, "Not started", "لم تبدأ"),
    IN_PROGRESS(1002, "In progress", "قيد التقدم"),
    COMPLETED(1003, "Completed", "مكتملة"),
    CANCELLED(1004, "Cancelled", "ملغاة");

    private final int code;
    private final String english;
    private final String arabic;

    LearningStatus(int code, String english, String arabic) {
        this.code = code;
        this.english = english;
        this.arabic = arabic;
    }

    public static LearningStatus fromCode(int code) {
        for (LearningStatus status : values()) {
            if (status.code == code) return status;
        }
        throw new ApiException(ErrorCode.UNKNOWN_CODE, code);
    }

    public EnumValueDto toDto() {
        return new EnumValueDto(code, english, arabic);
    }
}
