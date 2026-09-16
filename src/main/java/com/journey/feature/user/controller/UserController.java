package com.journey.feature.user.controller;

import com.journey.common.enums.UserRole;
import com.journey.feature.user.dto.CreateUserRequest;
import com.journey.feature.user.dto.UpdateUserRequest;
import com.journey.feature.user.dto.UserDto;
import com.journey.feature.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * SecurityConfig only requires authentication, so role checks live here, as in FileController.
     * Managing accounts — and reading the directory — is reserved for these roles.
     */
    private static final Set<String> USER_ADMIN_ROLES = Set.of("ROLE_MANAGER", "ROLE_ADMIN");
    private static final String ROLE_SENIOR = "ROLE_SENIOR";

    /** GET /api/users?role=&seniorId= — role accepts the code (1003) or the English name (Senior). */
    @GetMapping
    public ResponseEntity<List<UserDto>> getUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String seniorId) {

        Authentication auth = currentAuth();
        if (role != null && seniorId != null) {
            // A senior may list their own learners (journey list, metrics); nobody else's.
            boolean ownTeam = hasRole(auth, ROLE_SENIOR) && seniorId.equals(auth.getName());
            if (!ownTeam) requireUserAdmin(auth);
            return ResponseEntity.ok(userService.getLearnersBySenior(seniorId));
        }
        requireUserAdmin(auth);
        if (role != null) {
            return ResponseEntity.ok(userService.getUsersByRole(parseRole(role)));
        }
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /** GET /api/users/:id */
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable String id) {
        Authentication auth = currentAuth();
        boolean allowed = id.equals(auth.getName())
                || isUserAdmin(auth)
                || (hasRole(auth, ROLE_SENIOR) && userService.isLearnerOfSenior(id, auth.getName()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to view this user.");
        }
        return ResponseEntity.ok(userService.getById(id));
    }

    /** POST /api/users */
    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest req) {
        requireUserAdmin(currentAuth());
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(req));
    }

    /** PUT /api/users/:id */
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable String id,
                                              @RequestBody UpdateUserRequest req) {
        requireUserAdmin(currentAuth());
        return ResponseEntity.ok(userService.updateUser(id, req));
    }

    /** PATCH /api/users/:id  — used by Angular to reassign learner's senior */
    @PatchMapping("/{id}")
    public ResponseEntity<UserDto> patchUser(@PathVariable String id,
                                             @RequestBody Map<String, String> patch) {
        requireUserAdmin(currentAuth());
        UpdateUserRequest req = new UpdateUserRequest(null, null, null, null, patch.get("seniorId"));
        return ResponseEntity.ok(userService.updateUser(id, req));
    }

    /**
     * PATCH /api/users/me/language — the caller.s own display language, remembered so notification
     * email can be sent in it.
     *
     * <p>Scoped to the authenticated principal rather than taking an id: this is the one piece of a
     * user record a person should be able to change for themselves, and routing it through the
     * general update endpoint would mean letting them PATCH a user record.
     */
    @PatchMapping("/me/language")
    public ResponseEntity<Void> updateMyLanguage(@RequestBody Map<String, String> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        userService.updatePreferredLanguage(auth.getName(), body.get("language"));
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/users/:id */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        requireUserAdmin(currentAuth());
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    private Authentication currentAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return auth;
    }

    private boolean hasRole(Authentication auth, String authority) {
        return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private boolean isUserAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> USER_ADMIN_ROLES.contains(a.getAuthority()));
    }

    private void requireUserAdmin(Authentication auth) {
        if (!isUserAdmin(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only a Manager or Admin can do this.");
        }
    }

    /** Accepts either the numeric code or the English name, so old links keep working. */
    private UserRole parseRole(String role) {
        try {
            return UserRole.fromCode(Integer.parseInt(role.trim()));
        } catch (NumberFormatException notACode) {
            try {
                return UserRole.fromEnglish(role);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + role);
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role code: " + role);
        }
    }
}
