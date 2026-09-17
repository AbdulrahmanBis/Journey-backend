package com.journey.feature.auth.service;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.enums.UserRole;
import com.journey.config.JwtUtil;
import com.journey.feature.auth.dto.AuthResponse;
import com.journey.feature.auth.dto.LoginRequest;
import com.journey.feature.auth.dto.SignupRequest;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Self-service signup. Off by default: every account now needs a department, and a stranger
     * picking their own department would decide which manager can see them. Accounts are created by
     * HR, a Manager or an Admin instead — and once Entra ID sign-in arrives, by the directory.
     */
    @Value("${app.auth.self-signup-enabled:false}")
    private boolean selfSignupEnabled;

    /** The department a self-service learner joins, when signup is enabled. */
    @Value("${app.auth.self-signup-department-id:}")
    private String selfSignupDepartmentId;

    public AuthResponse login(LoginRequest req) {
        User user;
        try {
            user = userService.getEntityByEmail(req.email());
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        String role = UserRole.fromCode(user.getRole()).getEnglish();
        String token = jwtUtil.generateToken(user.getId(), role);
        return new AuthResponse(userService.toDto(user), token);
    }

    /** Self-service signup always creates a Learner account, when it is enabled at all. */
    public AuthResponse signup(SignupRequest req) {
        if (!selfSignupEnabled) {
            throw new ApiException(ErrorCode.SIGNUP_DISABLED);
        }
        User created = userService.createSelfServiceLearner(
                req.name(), req.email(), req.password(), selfSignupDepartmentId);
        String token = jwtUtil.generateToken(created.getId(), UserRole.LEARNER.getEnglish());
        return new AuthResponse(userService.toDto(created), token);
    }
}
