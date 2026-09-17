package com.journey.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The body of every error response.
 *
 * <pre>
 * {
 *   "code": "ERR-VALIDATION-FAILED", "status": 400,
 *   "english": "Some fields need attention.", "arabic": "بعض الحقول تحتاج إلى مراجعة.",
 *   "fields": [{ "field": "email", "code": "ERR-FIELD-EMAIL", "english": "…", "arabic": "…" }],
 *   "traceId": "7f3a9c1e2b4d"
 * }
 * </pre>
 *
 * {@code traceId} is also written to the server log with the failure, so a screenshot of the message is enough
 * to find the cause.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorDto(
        String code,
        int status,
        String english,
        String arabic,
        List<FieldErrorDto> fields,
        String traceId
) {
    public record FieldErrorDto(String field, String code, String english, String arabic) {}
}
