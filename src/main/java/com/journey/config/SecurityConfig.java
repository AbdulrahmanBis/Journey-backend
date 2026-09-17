package com.journey.config;

import com.journey.common.error.SecurityErrorHandlers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter.XFrameOptionsMode;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CorsConfig corsConfig;
    private final SecurityErrorHandlers errorHandlers;

    /**
     * File delivery. Matched on the raw URI, the same way {@link JwtAuthFilter} decides where a
     * {@code ?token=} parameter is accepted, so the two rules cannot drift apart.
     */
    private static final RequestMatcher FILE_DOWNLOADS =
            request -> request.getRequestURI().startsWith("/api/files/");

    private static final RequestMatcher EVERYTHING_ELSE =
            request -> !FILE_DOWNLOADS.matches(request);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))
                .headers(headers -> headers
                        /*
                          Spring Security's default is a blanket `X-Frame-Options: DENY`, which also
                          covered /api/files/**. That made the browser refuse to render a PDF
                          attachment in the learner view ("refused to connect"), because the PDF is
                          shown in an iframe. Replace the blanket header with a per-path policy:
                          still DENY everywhere, but file downloads carry a CSP `frame-ancestors`
                          naming exactly the origins CORS already trusts. Arbitrary sites therefore
                          still cannot frame an attachment, and nothing else became framable.
                         */
                        .frameOptions(frame -> frame.disable())
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                EVERYTHING_ELSE,
                                new XFrameOptionsHeaderWriter(XFrameOptionsMode.DENY)))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                FILE_DOWNLOADS,
                                new StaticHeadersWriter(
                                        "Content-Security-Policy",
                                        "frame-ancestors " + corsConfig.frameAncestors())))
                )
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        // The container error page; it only renders the error body (see ApiErrorController).
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
