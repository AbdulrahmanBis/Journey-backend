package com.journey.common.error;

import com.journey.config.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security rejects a request before any controller runs, so the controller advice never sees it. These write the
 * same {@link ApiErrorDto} body: 401 {@code ERR-AUTH-SESSION-EXPIRED} for an expired token (the client sends the
 * person back to sign in), 401 {@code ERR-AUTH-REQUIRED} for a missing or forged one, 403 otherwise.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** Set by the JWT filter when a token was sent but could not be accepted. */
    public static final String TOKEN_REJECTION = SecurityErrorHandlers.class.getName() + ".tokenRejection";

    private final JsonMapper json;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        Object rejection = request.getAttribute(TOKEN_REJECTION);
        write(response, rejection instanceof ErrorCode code ? code : ErrorCode.AUTH_REQUIRED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response, ErrorCode.FORBIDDEN);
    }

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getOutputStream(), GlobalExceptionHandler.error(code, GlobalExceptionHandler.traceId()));
    }
}
