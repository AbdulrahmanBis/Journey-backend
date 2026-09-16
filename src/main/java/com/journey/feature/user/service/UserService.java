package com.journey.feature.user.service;

import com.journey.common.enums.UserRole;
import com.journey.common.security.AccessPolicy;
import com.journey.common.service.IdGeneratorService;
import com.journey.feature.department.dto.DepartmentDto;
import com.journey.feature.department.entity.Department;
import com.journey.feature.department.repository.DepartmentRepository;
import com.journey.feature.user.dto.CreateUserRequest;
import com.journey.feature.user.dto.UpdateUserRequest;
import com.journey.feature.user.dto.UserDto;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * User accounts. Every public method that reads or changes someone else's account goes through
 * {@link AccessPolicy}, so the department rules apply however the service is reached.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdGeneratorService idGenerator;
    private final AccessPolicy access;

    // ─── Queries ──────────────────────────────────────────────────────────────

    /**
     * The people the caller may see, optionally narrowed.
     *
     * <ul>
     *   <li>Senior: their own learners (role and department filters do not widen that).</li>
     *   <li>Manager: their own department.</li>
     *   <li>HR, Admin: everyone, or one department when {@code departmentId} is given.</li>
     *   <li>Learner: nobody — there is no directory view for a learner.</li>
     * </ul>
     *
     * @param seniorId narrows to one senior's learners, inside the scope above — it never widens it
     */
    public List<UserDto> list(UserRole role, String departmentId, String seniorId) {
        User actor = access.actor();

        if (AccessPolicy.hasRole(actor, UserRole.SENIOR)) {
            if (seniorId != null && !seniorId.equals(actor.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A Senior can only list their own learners.");
            }
            List<User> learners = userRepository.findByRoleAndSeniorId(UserRole.LEARNER.getCode(), actor.getId());
            return toDtos(learners.stream().filter(u -> role == null || matches(u, role)).toList());
        }
        if (!AccessPolicy.hasRole(actor, AccessPolicy.USER_ADMINS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your role cannot list people.");
        }

        String scope = access.departmentScope(departmentId);
        List<User> users;
        if (scope == null) {
            users = role == null ? userRepository.findAll() : userRepository.findByRole(role.getCode());
        } else {
            users = role == null ? userRepository.findByDepartmentId(scope)
                    : userRepository.findByRoleAndDepartmentId(role.getCode(), scope);
        }
        if (seniorId != null) {
            users = users.stream().filter(u -> seniorId.equals(u.getSeniorId())).toList();
        }
        return toDtos(users);
    }

    public UserDto getById(String id) {
        return toDto(access.requireViewable(id));
    }

    /** Internal — returns the entity (AuthService needs the password hash). No access check. */
    public User getEntityByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    // ─── Mutations ────────────────────────────────────────────────────────────

    @Transactional
    public UserDto createUser(CreateUserRequest req) {
        access.requireRole(AccessPolicy.USER_ADMINS);
        UserRole role = resolveRole(req.role());
        requireDepartment(req.departmentId());
        access.requireCanAssign(role, req.departmentId());

        if (userRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        String seniorId = role == UserRole.LEARNER ? blankToNull(req.seniorId()) : null;
        requireValidSenior(seniorId, req.departmentId());

        User user = User.builder()
                .id(idGenerator.next(IdGeneratorService.USER, "u-"))
                .name(req.name())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .role(role.getCode())
                .departmentId(req.departmentId())
                .seniorId(seniorId)
                .build();
        return toDto(userRepository.save(user));
    }

    /**
     * Self-service signup: always a Learner, in the given department, with no senior. No access
     * check — the caller is not signed in yet. AuthService decides whether signup is allowed at all.
     */
    @Transactional
    public User createSelfServiceLearner(String name, String email, String password, String departmentId) {
        requireDepartment(departmentId);
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        return userRepository.save(User.builder()
                .id(idGenerator.next(IdGeneratorService.USER, "u-"))
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(UserRole.LEARNER.getCode())
                .departmentId(departmentId)
                .build());
    }

    @Transactional
    public UserDto updateUser(String id, UpdateUserRequest req) {
        User user = findOrThrow(id);
        access.requireCanManageAccount(user);

        UserRole newRole = req.role() != null ? resolveRole(req.role()) : AccessPolicy.roleOf(user);
        String newDepartment = req.departmentId() != null ? req.departmentId() : user.getDepartmentId();
        boolean roleChanges = newRole != AccessPolicy.roleOf(user);
        boolean departmentChanges = !Objects.equals(newDepartment, user.getDepartmentId());

        if (roleChanges || departmentChanges) {
            requireDepartment(newDepartment);
            access.requireCanAssign(newRole, newDepartment);
        }

        // A senior who leaves their department (or stops being a senior) would leave learners pointing
        // at someone outside their department. Make the caller reassign them rather than guess.
        if (AccessPolicy.hasRole(user, UserRole.SENIOR) && (departmentChanges || newRole != UserRole.SENIOR)) {
            int learners = userRepository.findByRoleAndSeniorId(UserRole.LEARNER.getCode(), user.getId()).size();
            if (learners > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        user.getName() + " still has " + learners + " learner(s). Reassign them first.");
            }
        }

        if (req.name() != null) user.setName(req.name());
        if (req.email() != null && !req.email().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(req.email())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
            }
            user.setEmail(req.email());
        }
        if (req.password() != null && !req.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.password()));
        }
        user.setRole(newRole.getCode());
        user.setDepartmentId(newDepartment);

        // Only learners have a senior, and it must be a senior of their own department.
        if (newRole != UserRole.LEARNER || Boolean.TRUE.equals(req.clearSenior())) {
            user.setSeniorId(null);
        } else if (req.seniorId() != null) {
            String seniorId = blankToNull(req.seniorId());
            requireValidSenior(seniorId, newDepartment);
            user.setSeniorId(seniorId);
        } else if (departmentChanges) {
            // Moved without naming a new senior: the old one is in the old department.
            user.setSeniorId(null);
        }

        return toDto(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(String id) {
        User user = findOrThrow(id);
        access.requireCanManageAccount(user);
        if (user.getId().equals(access.actor().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot delete your own account.");
        }
        userRepository.delete(user);
    }

    /**
     * Stores the language the person picked in the UI, used to choose which language to email them
     * in. Only ever called for the caller's own id — see UserController.
     */
    @Transactional
    public void updatePreferredLanguage(String id, String language) {
        String tag = language == null ? "" : language.trim().toLowerCase();
        if (!tag.equals("en") && !tag.equals("ar")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported language: " + language + ". Expected \"en\" or \"ar\".");
        }
        User user = findOrThrow(id);
        user.setPreferredLanguage(tag);
        userRepository.save(user);
    }

    // ─── Mapper ───────────────────────────────────────────────────────────────

    public UserDto toDto(User u) {
        Department department = departmentRepository.findById(u.getDepartmentId()).orElse(null);
        return toDto(u, department);
    }

    private List<UserDto> toDtos(List<User> users) {
        Map<String, Department> departments = departmentRepository.findAll().stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
        return users.stream().map(u -> toDto(u, departments.get(u.getDepartmentId()))).toList();
    }

    private UserDto toDto(User u, Department department) {
        DepartmentDto departmentDto = department == null ? null
                : new DepartmentDto(department.getId(), department.getNameEn(), department.getNameAr(), null);
        return new UserDto(u.getId(), u.getName(), u.getEmail(),
                UserRole.fromCode(u.getRole()).toDto(), departmentDto, u.getSeniorId(), u.getCreatedAt());
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    /** Turns an inbound role code into the enum, answering with 400 rather than 500 when it's bogus. */
    private UserRole resolveRole(Integer code) {
        try {
            return UserRole.fromCode(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role code: " + code);
        }
    }

    private void requireDepartment(String departmentId) {
        if (departmentId == null || departmentId.isBlank() || !departmentRepository.existsById(departmentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown department: " + departmentId);
        }
    }

    /** A learner's senior must exist, be a Senior, and work in the learner's department. */
    private void requireValidSenior(String seniorId, String departmentId) {
        if (seniorId == null) return;
        User senior = userRepository.findById(seniorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown senior: " + seniorId));
        if (!AccessPolicy.hasRole(senior, UserRole.SENIOR)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, senior.getName() + " is not a Senior.");
        }
        if (!Objects.equals(senior.getDepartmentId(), departmentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    senior.getName() + " is in a different department.");
        }
    }

    private static boolean matches(User user, UserRole role) {
        return user.getRole() != null && user.getRole() == role.getCode();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private User findOrThrow(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }
}
