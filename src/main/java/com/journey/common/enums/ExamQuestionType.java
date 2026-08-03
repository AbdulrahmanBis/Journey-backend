package com.journey.common.enums;

public enum ExamQuestionType {
    SUBMITTED(1001, "Submitted", "تم التسليم"),
    PENDING(1002, "Pending", "قيد الانتظار"),
    FAILED(1003, "Failed", "راسب"),
    PASSED(1004, "Passed", "ناجح");

    private final int code;
    private final String english;
    private final String arabic;

    ExamQuestionType(int code, String english, String arabic) {
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
}
