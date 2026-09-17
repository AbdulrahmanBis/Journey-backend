package com.journey.common.error;

import com.journey.config.GlobalExceptionHandler;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The servlet container's last resort ({@code /error}), reached only when something fails outside Spring MVC —
 * in a filter, say. Answers with the same error body instead of Spring Boot's default page.
 */
@Slf4j
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiErrorDto> error(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttribute instanceof Integer s ? s : 500;
        ErrorCode code = switch (status) {
            case 400 -> ErrorCode.MALFORMED_REQUEST;
            case 401 -> ErrorCode.AUTH_REQUIRED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.ROUTE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 413 -> ErrorCode.FILE_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_CONTENT_TYPE;
            default -> ErrorCode.INTERNAL;
        };
        String traceId = GlobalExceptionHandler.traceId();
        if (code == ErrorCode.INTERNAL) {
            log.error("[{}] Request failed outside the controllers: {} {}", traceId,
                    request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI),
                    request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
        }
        Object args = code == ErrorCode.FILE_TOO_LARGE ? "?" : null;
        ApiErrorDto body = args == null ? GlobalExceptionHandler.error(code, traceId)
                : GlobalExceptionHandler.error(code, traceId, args);
        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
