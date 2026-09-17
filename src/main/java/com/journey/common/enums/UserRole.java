package com.journey.common.enums;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.dto.EnumValueDto;

import java.util.Arrays;

public enum UserRole{

    ADMIN(1001, "Admin", "مدير النظام"),
    MANAGER(1002, "Manager", "مدير"),
    SENIOR(1003, "Senior", "مشرف"),
    LEARNER(1004, "Learner", "متعلم"),
    /**
     * Human resources. Sees and manages people across every department — the reach the Manager
     * role used to have — while Manager is now limited to its own department. Added after the
     * others, so it takes the next code rather than renumbering anyone.
     */
    HR(1005, "HR", "الموارد البشرية");


    private final int code;
    private final String english;
    private final String arabic;
    UserRole(int code, String english, String arabic) {
        this.code = code;
        this.english = english;
        this.arabic = arabic;
    }

    public int getCode() {
        return code;
    }

    public String getEnglish() {
        return english;
    }

    public String getArabic() {
        return arabic;
    }

    public static UserRole fromCode(int code) {
        return Arrays.stream(values())
                .filter(r -> r.code == code)
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_CODE, code));
    }
    public static UserRole fromEnglish(String english) {
        return Arrays.stream(values())
                .filter(role -> role.getEnglish().equalsIgnoreCase(english))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_CODE, english));
    }

    public EnumValueDto toDto() {
        return new EnumValueDto(code, english, arabic);
    }
}
