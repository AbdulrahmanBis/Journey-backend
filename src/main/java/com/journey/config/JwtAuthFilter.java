package com.journey.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.journey.common.error.ErrorCode;
import com.journey.common.error.SecurityErrorHandlers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            ErrorCode rejection = jwtUtil.rejectionOf(token);
            if (rejection != null) {
                // Left unauthenticated; the entry point tells the client why, so it can say "session expired".
                request.setAttribute(SecurityErrorHandlers.TOKEN_REJECTION, rejection);
            } else {
                String userId = jwtUtil.extractUserId(token);
                Object roleClaim = jwtUtil.extractClaims(token).get("role");
                String role = roleClaim != null ? roleClaim.toString().toUpperCase() : "LEARNER";

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Normally the bearer header. Media elements ({@code <img>}, {@code <video>}) cannot send
     * headers, and fetching them through XHR into a blob would break range requests — and with them
     * video seeking — so {@code /api/files/} additionally accepts {@code ?token=}.
     *
     * <p>Deliberately limited to that one prefix: tokens in URLs can leak through access logs and
     * Referer headers, so the rest of the API stays header-only. Short-lived signed URLs would be
     * the stronger fix if these ever leave the internal network.
     */
    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/files/")) {
            String queryToken = request.getParameter("token");
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken;
            }
        }
        return null;
    }
}
