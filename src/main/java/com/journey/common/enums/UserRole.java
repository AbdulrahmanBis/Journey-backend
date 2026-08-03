package com.journey.common.enums;

import java.util.Arrays;
import java.util.Objects;

public enum UserRole{

    ADMIN(1001, "Admin", "مدير النظام"),
    MANAGER(1002, "Manager", "مدير"),
    SENIOR(1003, "Senior", "خبير"),
    LEARNER(1004, "Learner", "متعلم");


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
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + code));
    }
    public static UserRole fromEnglish(String english) {
        return Arrays.stream(values())
                .filter(role -> role.getEnglish().equalsIgnoreCase(english))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + english));
    }
}
