package com.journey.common.enums;

import com.journey.common.dto.EnumValueDto;
import lombok.Getter;

/**
 * How a notification reaches its recipient.
 *
 * <p>Stored in {@code notifications.channel} as the {@code code}, following the same 1001-based
 * convention as every other enum in the system.
 *
 * <p>{@code IN_APP} is the bell in the top bar and is the only channel that persists a row the user
 * can come back to. {@code MAIL} renders the template's {@code mail.xml} and sends it. {@code PUSH}
 * is browser Web Push — reserved, not yet implemented; it needs VAPID keys and a service worker,
 * and nothing dispatches it today.
 */
@Getter
public enum NotificationChannel {

    IN_APP(1001, "In-app", "داخل التطبيق"),
    MAIL(1002, "Email", "بريد إلكتروني"),
    PUSH(1003, "Push", "إشعار فوري");

    private final int code;
    private final String english;
    private final String arabic;

    NotificationChannel(int code, String english, String arabic) {
        this.code = code;
        this.english = english;
        this.arabic = arabic;
    }

    public EnumValueDto toDto() {
        return new EnumValueDto(code, english, arabic);
    }

    public static NotificationChannel fromCode(int code) {
        for (NotificationChannel channel : values()) {
            if (channel.code == code) {
                return channel;
            }
        }
        throw new IllegalArgumentException("Unknown notification channel code: " + code);
    }

    /**
     * Resolves the name used in a template's {@code config.json} — {@code "IN_APP"}, {@code "MAIL"}.
     * Template files are edited by hand, so the error names the valid options.
     */
    public static NotificationChannel fromKey(String key) {
        for (NotificationChannel channel : values()) {
            if (channel.name().equalsIgnoreCase(key)) {
                return channel;
            }
        }
        throw new IllegalArgumentException(
                "Unknown notification channel \"" + key + "\". Expected one of: IN_APP, MAIL, PUSH.");
    }
}
