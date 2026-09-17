package com.journey.common.enums;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.dto.EnumValueDto;
import lombok.Getter;

@Getter
public enum ItemStatus {

    // Wording is display only; the codes are what is stored and compared.
    NEW(1001, "new", "جديد"),
    /** The learner is working on it. */
    REFLECT(1002, "in progress", "قيد التنفيذ"),
    /** The learner is done and waiting for the reviewer. */
    RESPONSE(1003, "waiting for review", "بانتظار المراجعة"),
    COMPLETED(1004, "completed", "مكتمل"),
    CANCELLED(1005, "cancelled", "ملغي");

    private final int code;
    private final String english;
    private final String arabic;

    ItemStatus(int code, String english, String arabic) {
        this.code = code;
        this.english = english;
        this.arabic = arabic;
    }

    public static ItemStatus fromCode(int code) {
        for (ItemStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new ApiException(ErrorCode.UNKNOWN_CODE, code);
    }

    public static ItemStatus fromEnglish(String english) {
        for (ItemStatus status : values()) {
            if (status.english.equalsIgnoreCase(english)) {
                return status;
            }
        }
        throw new ApiException(ErrorCode.UNKNOWN_CODE, english);
    }

    public static ItemStatus fromArabic(String arabic) {
        for (ItemStatus status : values()) {
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