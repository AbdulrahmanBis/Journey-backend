package com.journey.common.enums;

import com.journey.common.dto.EnumValueDto;
import lombok.Getter;

/** What a catalog entry is. */
@Getter
public enum CatalogItemType {

    JOURNEY(1001, "Journey", "رحلة"),
    PACKAGE(1002, "Package", "حزمة");

    private final int code;
    private final String english;
    private final String arabic;

    CatalogItemType(int code, String english, String arabic) {
        this.code = code;
        this.english = english;
        this.arabic = arabic;
    }

    public static CatalogItemType fromCode(int code) {
        for (CatalogItemType type : values()) {
            if (type.code == code) return type;
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }

    public EnumValueDto toDto() {
        return new EnumValueDto(code, english, arabic);
    }
}
