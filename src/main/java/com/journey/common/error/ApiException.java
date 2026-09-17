package com.journey.common.error;

import java.util.Arrays;
import java.util.List;

/**
 * The one exception services throw for an expected failure: a catalog {@link ErrorCode} plus the values for its
 * placeholders. The global handler turns it into the error body, in both languages.
 *
 * <pre>
 *   throw new ApiException(ErrorCode.JOURNEY_NOT_FOUND);
 *   throw new ApiException(ErrorCode.NOT_A_SENIOR, person.getName());
 *   throw new ApiException(ErrorCode.GRANT_ROLE_NOT_ALLOWED, ApiException.text(role.getEnglish(), role.getArabic()));
 * </pre>
 */
public class ApiException extends RuntimeException {

    /** A placeholder value that is spelled differently in each language. */
    public record Text(String english, String arabic) {}

    private final ErrorCode error;
    private final transient List<Object> args;

    public ApiException(ErrorCode error, Object... args) {
        super(error.getCode() + ": " + format(error.getEnglish(), Arrays.asList(args), true));
        this.error = error;
        this.args = Arrays.asList(args);
    }

    public static Text text(String english, String arabic) {
        return new Text(english, arabic);
    }

    public ErrorCode getError() { return error; }

    public String english() { return format(error.getEnglish(), args, true); }

    public String arabic() { return format(error.getArabic(), args, false); }

    static String format(String template, List<Object> args, boolean english) {
        String out = template;
        for (int i = 0; i < args.size(); i++) {
            Object arg = args.get(i);
            String value = arg instanceof Text t ? (english ? t.english() : t.arabic()) : String.valueOf(arg);
            out = out.replace("{" + i + "}", value);
        }
        return out;
    }
}
