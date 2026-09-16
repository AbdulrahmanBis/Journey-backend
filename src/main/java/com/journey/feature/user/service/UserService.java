package com.journey.feature.user.service;

import com.journey.common.enums.UserRole;
import com.journey.common.service.IdGeneratorService;
import com.journey.feature.user.dto.CreateUserRequest;
import com.journey.feature.user.dto.UpdateUserRequest;
import com.journey.feature.user.dto.UserDto;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdGeneratorService idGenerator;

    // ─── Queries ──────────────────────────────────────────────────────────────

    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream().map(this::toDto).toList();
    }

    public List<UserDto> getUsersByRole(UserRole role) {
        return userRepository.findByRole(role.getCode()).stream().map(this::toDto).toList();
    }

    public List<UserDto> getLearnersBySenior(String seniorId) {
        return userRepository.findByRoleAndSeniorId(UserRole.LEARNER.getCode(), seniorId)
                .stream().map(this::toDto).toList();
    }

    public UserDto getById(String id) {
        return toDto(findOrThrow(id));
    }

    /** Internal — returns entity (used by AuthService and services that need the password). */
    public User getEntityByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public User getEntityById(String id) {
        return findOrThrow(id);
    }

    // ─── Mutations ────────────────────────────────────────────────────────────

    public UserDto createUser(CreateUserRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        UserRole role = resolveRole(req.role());
        User user = User.builder()
                .id(idGenerator.next(IdGeneratorService.USER, "u-"))
                .name(req.name())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .role(role.getCode())
                .seniorId(role == UserRole.LEARNER ? req.seniorId() : null)
                .build();
        return toDto(userRepository.save(user));
    }

    public UserDto updateUser(String id, UpdateUserRequest req) {
        User user = findOrThrow(id);
        if (req.name() != null) user.setName(req.name());
        if (req.email() != null) user.setEmail(req.email());
        if (req.password() != null) user.setPassword(passwordEncoder.encode(req.password()));
        if (req.role() != null) user.setRole(resolveRole(req.role()).getCode());
        if (req.seniorId() != null) user.setSeniorId(req.seniorId());
        return toDto(userRepository.save(user));
    }

    public void deleteUser(String id) {
        User user = findOrThrow(id);
        userRepository.delete(user);
    }

    // ─── Mapper ───────────────────────────────────────────────────────────────

    public UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getName(), u.getEmail(),
                UserRole.fromCode(u.getRole()).toDto(), u.getSeniorId(), u.getCreatedAt());
    }

    /** Turns an inbound role code into the enum, answering with 400 rather than 500 when it's bogus. */
    private UserRole resolveRole(Integer code) {
        try {
            return UserRole.fromCode(code);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role code: " + code);
        }
    }

    private User findOrThrow(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }
}
