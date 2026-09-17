package com.journey.common.enums;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.dto.EnumValueDto;
import lombok.Getter;

/**
 * What a journey-item attachment is, which decides how the client renders it.
 *
 * <p>Stored in {@code journey_item_attachments.kind} as the {@code code}, following the same
 * 1001-based convention as every other enum in the system.
 *
 * <p>{@code LINK}, {@code VIDEO_EMBED} and {@code IFRAME} carry an external {@code url} and have no
 * stored bytes. {@code IMAGE}, {@code PDF}, {@code DOCUMENT} and {@code VIDEO_FILE} are uploaded and
 * carry a {@code storageKey}. {@code VIDEO_FILE} is the only kind served with HTTP Range support,
 * which is what lets the player seek instead of downloading the whole file first.
 */
@Getter
public enum AttachmentKind {

    LINK(1001, "Link", "رابط"),
    IMAGE(1002, "Image", "صورة"),
    PDF(1003, "PDF", "ملف PDF"),
    DOCUMENT(1004, "Document", "مستند"),
    VIDEO_EMBED(1005, "Video (embedded)", "فيديو مضمّن"),
    VIDEO_FILE(1006, "Video (uploaded)", "فيديو مرفوع"),
    IFRAME(1007, "Embedded page", "صفحة مضمّنة");

    private final int code;
    private final String english;
    private final String arabic;

    AttachmentKind(int code, String english, String arabic) {
        this.code = code;
        this.english = english;
        this.arabic = arabic;
    }

    public EnumValueDto toDto() {
        return new EnumValueDto(code, english, arabic);
    }

    /** True when this kind's content lives in storage rather than at an external URL. */
    public boolean isUploaded() {
        return this == IMAGE || this == PDF || this == DOCUMENT || this == VIDEO_FILE;
    }

    public static AttachmentKind fromCode(int code) {
        for (AttachmentKind kind : values()) {
            if (kind.code == code) {
                return kind;
            }
        }
        throw new ApiException(ErrorCode.UNKNOWN_CODE, code);
    }

    public static AttachmentKind fromEnglish(String english) {
        for (AttachmentKind kind : values()) {
            if (kind.english.equalsIgnoreCase(english)) {
                return kind;
            }
        }
        throw new ApiException(ErrorCode.UNKNOWN_CODE, english);
    }
}
