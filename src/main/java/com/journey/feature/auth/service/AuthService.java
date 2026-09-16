package com.journey.feature.auth.service;

import com.journey.common.enums.UserRole;
import com.journey.config.JwtUtil;
import com.journey.feature.auth.dto.AuthResponse;
import com.journey.feature.auth.dto.LoginRequest;
import com.journey.feature.auth.dto.SignupRequest;
import com.journey.feature.user.dto.CreateUserRequest;
import com.journey.feature.user.dto.UserDto;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse login(LoginRequest req) {
        User user;
        try {
            user = userService.getEntityByEmail(req.email());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
        }

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
        }

        String role = UserRole.fromCode(user.getRole()).getEnglish();
        String token = jwtUtil.generateToken(user.getId(), role);
        return new AuthResponse(userService.toDto(user), token);
    }

    /** Self-service signup always creates a Learner account. */
    public AuthResponse signup(SignupRequest req) {
        UserDto created = userService.createUser(new CreateUserRequest(
                req.name(), req.email(), req.password(), UserRole.LEARNER.getCode(), null
        ));
        String token = jwtUtil.generateToken(created.id(), UserRole.LEARNER.getEnglish());
        return new AuthResponse(created, token);
    }
}
