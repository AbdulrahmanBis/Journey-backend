package com.journey.common.enums;

import com.journey.common.dto.EnumValueDto;
import lombok.Getter;

/** A learner at a glance, for the team table. */
@Getter
public enum LearnerHealth {

    OVERDUE(1001, "Overdue", "متأخر"),
    AT_RISK(1002, "At risk", "معرّض للتأخر"),
    ON_TRACK(1003, "On track", "على المسار"),
    NOT_STARTED(1004, "Nothing assigned", "لا شيء مُسند"),
    DONE(1005, "All done", "أنهى كل شيء");

    private final int code;
    private final String english;
    private final String arabic;

    LearnerHealth(int code, String english, String arabic) {
        this.code = code;
        this.english = english;
        this.arabic = arabic;
    }

    public EnumValueDto toDto() {
        return new EnumValueDto(code, english, arabic);
    }
}
