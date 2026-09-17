package com.journey.config;

import com.journey.common.error.ApiErrorDto;
import com.journey.common.error.ApiErrorDto.FieldErrorDto;
import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns every failure into the one error body ({@link ApiErrorDto}): a catalog code plus English and Arabic text.
 *
 * <p>Expected failures are {@link ApiException}s thrown by the services. Framework failures caused by a bad
 * request — unknown address, unreadable JSON, a letter where a number belongs, a missing parameter, a file over
 * the limit — are mapped to their own 4xx codes here, so they never surface as a 500. Anything left is a bug:
 * it answers {@code ERR-INTERNAL} and is logged with the same {@code traceId} the client receives.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${app.storage.max-file-size-bytes:209715200}")
    private long maxFileBytes;

    // ─── Expected, thrown by our own code ────────────────────────────────────────────────

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorDto> handleApi(ApiException ex) {
        String traceId = traceId();
        if (ex.getError().getStatus().is5xxServerError()) {
            log.error("[{}] {}", traceId, ex.getMessage(), ex);
        } else {
            log.debug("[{}] {}", traceId, ex.getMessage());
        }
        return respond(new ApiErrorDto(ex.getError().getCode(), ex.getError().getStatus().value(),
                ex.english(), ex.arabic(), List.of(), traceId));
    }

    // ─── Validation ─────────────────────────────────────────────────────────────────────

    /** {@code @Valid} request bodies. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDto> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDto> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> field(e.getField(), constraintCode(e)))
                .toList();
        return validation(fields);
    }

    /** Constraints on {@code @RequestParam} / {@code @PathVariable} arguments. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorDto> handleMethodValidation(HandlerMethodValidationException ex) {
        List<FieldErrorDto> fields = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream().map(e -> field(
                        r.getMethodParameter().getParameterName(), constraintCode(e.getCodes()))))
                .toList();
        return validation(fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorDto> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDto> fields = ex.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    String name = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    String annotation = v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
                    return field(name, constraintCode(new String[]{annotation}));
                })
                .toList();
        return validation(fields);
    }

    // ─── Malformed requests ─────────────────────────────────────────────────────────────

    /** Unreadable JSON, or a value of the wrong type ("abc" for a number, "not-a-date" for a date). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorDto> handleUnreadable(HttpMessageNotReadableException ex) {
        String fieldName = null;
        for (Throwable t = ex; t != null && t.getCause() != t; t = t.getCause()) {
            if (t instanceof JacksonException jackson && jackson.getPath() != null && !jackson.getPath().isEmpty()) {
                fieldName = jackson.getPath().stream()
                        .map(r -> r.getPropertyName() != null ? r.getPropertyName() : String.valueOf(r.getIndex()))
                        .collect(Collectors.joining("."));
                break;
            }
        }
        if (fieldName != null && !fieldName.isBlank()) {
            return validation(List.of(field(fieldName, ErrorCode.FIELD_INVALID)));
        }
        return simple(ErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorDto> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return simple(ErrorCode.INVALID_PARAMETER, ex.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorDto> handleMissingParameter(MissingServletRequestParameterException ex) {
        return simple(ErrorCode.MISSING_PARAMETER, ex.getParameterName());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorDto> handleMissingPart(MissingServletRequestPartException ex) {
        return "file".equals(ex.getRequestPartName())
                ? simple(ErrorCode.FILE_REQUIRED)
                : simple(ErrorCode.MISSING_PARAMETER, ex.getRequestPartName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorDto> handleTooLarge(MaxUploadSizeExceededException ex) {
        return simple(ErrorCode.FILE_TOO_LARGE, maxFileBytes / 1024 / 1024);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorDto> handleMultipart(MultipartException ex) {
        return simple(ErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class, MissingPathVariableException.class})
    public ResponseEntity<ApiErrorDto> handleNoRoute(Exception ex) {
        return simple(ErrorCode.ROUTE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorDto> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return simple(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorDto> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return simple(ErrorCode.UNSUPPORTED_CONTENT_TYPE);
    }

    // ─── Security and data ──────────────────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorDto> handleAccessDenied(AccessDeniedException ex) {
        return simple(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorDto> handleAuthentication(AuthenticationException ex) {
        return simple(ErrorCode.AUTH_REQUIRED);
    }

    /** Two people saving the same row at once. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorDto> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return simple(ErrorCode.CONCURRENT_UPDATE);
    }

    /**
     * A database constraint caught what the service did not (a duplicate, a row still referenced). Answered as a
     * conflict, but logged in full: it means a check is missing in the service.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorDto> handleIntegrity(DataIntegrityViolationException ex) {
        String traceId = traceId();
        log.warn("[{}] Database constraint rejected a change — a service-level check is missing", traceId, ex);
        return respond(error(ErrorCode.DATA_CONFLICT, traceId));
    }

    /** The browser went away mid-response (typically a video seek). Nothing to answer. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientGone(AsyncRequestNotUsableException ex) {
        log.debug("Client disconnected: {}", ex.getMessage());
    }

    // ─── Everything else is a bug ───────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handleGeneric(Exception ex) {
        String traceId = traceId();
        log.error("[{}] Unhandled exception", traceId, ex);
        return respond(error(ErrorCode.INTERNAL, traceId));
    }

    // ─── Building responses ─────────────────────────────────────────────────────────────

    public static ApiErrorDto error(ErrorCode code, String traceId, Object... args) {
        ApiException ex = new ApiException(code, args);
        return new ApiErrorDto(code.getCode(), code.getStatus().value(), ex.english(), ex.arabic(), List.of(), traceId);
    }

    public static String traceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static ResponseEntity<ApiErrorDto> simple(ErrorCode code, Object... args) {
        return respond(error(code, traceId(), args));
    }

    private static ResponseEntity<ApiErrorDto> validation(List<FieldErrorDto> fields) {
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return respond(new ApiErrorDto(code.getCode(), code.getStatus().value(), code.getEnglish(), code.getArabic(),
                fields, traceId()));
    }

    private static ResponseEntity<ApiErrorDto> respond(ApiErrorDto body) {
        return ResponseEntity.status(body.status()).body(body);
    }

    private static FieldErrorDto field(String name, ErrorCode code) {
        return new FieldErrorDto(name, code.getCode(), code.getEnglish(), code.getArabic());
    }

    private static ErrorCode constraintCode(FieldError error) {
        return error.isBindingFailure() ? ErrorCode.FIELD_INVALID : constraintCode(error.getCodes());
    }

    /** Maps a Bean Validation annotation (first entry of the resolvable codes) onto a field error code. */
    private static ErrorCode constraintCode(String[] codes) {
        String annotation = codes == null || codes.length == 0 ? "" : codes[codes.length - 1];
        return switch (annotation) {
            case "NotBlank", "NotNull", "NotEmpty" -> ErrorCode.FIELD_REQUIRED;
            case "Size", "Length" -> ErrorCode.FIELD_LENGTH;
            case "Email" -> ErrorCode.FIELD_EMAIL;
            case "Min", "Max", "Positive", "PositiveOrZero", "Negative", "DecimalMin", "DecimalMax",
                 "Future", "FutureOrPresent", "Past", "PastOrPresent" -> ErrorCode.FIELD_RANGE;
            case "Pattern" -> ErrorCode.FIELD_FORMAT;
            default -> ErrorCode.FIELD_INVALID;
        };
    }
}
