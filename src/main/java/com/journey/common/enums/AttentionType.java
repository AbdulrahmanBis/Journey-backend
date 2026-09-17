package com.journey.common.enums;

import com.journey.common.dto.EnumValueDto;
import lombok.Getter;

/** Why something is on a staff member's "needs your attention" list, most urgent first. */
@Getter
public enum AttentionType {

    OVERDUE(1001, "Overdue", "متأخرة"),
    EXAM_TO_GRADE(1002, "Exam to grade", "اختبار بانتظار التصحيح"),
    AWAITING_REVIEW(1003, "Waiting for review", "بانتظار المراجعة"),
    UNANSWERED_NOTE(1004, "Unanswered note", "ملاحظة بلا رد"),
    INACTIVE(1005, "No recent activity", "لا نشاط حديث"),
    NOTHING_ASSIGNED(1006, "Nothing assigned", "لا شيء مُسند"),
    NO_SENIOR(1007, "No senior", "بلا مشرف");

    private final int code;
    private final String english;
    private final String arabic;

    AttentionType(int code, String english, String arabic) {
        this.code = code;
        this.english = english;
        this.arabic = arabic;
    }

    public EnumValueDto toDto() {
        return new EnumValueDto(code, english, arabic);
    }
}
