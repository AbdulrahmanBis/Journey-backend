package com.journey.common.enums;

import lombok.Getter;

@Getter
public enum ItemStatus {

    NEW(1001, "new", "جديد"),
    REFLECT(1002, "reflect", "راجع"),
    RESPONSE(1003, "response", "إجابة"),
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
        throw new IllegalArgumentException("Unknown code: " + code);
    }

    public static ItemStatus fromEnglish(String english) {
        for (ItemStatus status : values()) {
            if (status.english.equalsIgnoreCase(english)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown english: " + english);
    }

    public static ItemStatus fromArabic(String arabic) {
        for (ItemStatus status : values()) {
            if (status.arabic.equals(arabic)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown arabic: " + arabic);
    }
}