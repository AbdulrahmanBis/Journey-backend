package com.journey.feature.user.controller;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.enums.UserRole;
import com.journey.common.security.AccessPolicy;
import com.journey.feature.user.dto.CreateUserRequest;
import com.journey.feature.user.dto.UpdateUserRequest;
import com.journey.feature.user.dto.UserDto;
import com.journey.feature.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * User accounts. Who may see or change whom — including department scope — is decided in
 * UserService through AccessPolicy, not here.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccessPolicy access;

    /**
     * GET /api/users?role=&departmentId=&seniorId=
     * role accepts the code (1003) or the English name (Senior). Results are always limited to the
     * people the caller may see; the filters only narrow that.
     */
    @GetMapping
    public ResponseEntity<List<UserDto>> getUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String seniorId) {
        return ResponseEntity.ok(userService.list(role == null ? null : parseRole(role), departmentId, seniorId));
    }

    /** GET /api/users/:id */
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable String id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    /** POST /api/users */
    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(req));
    }

    /** PUT /api/users/:id */
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable String id, @RequestBody UpdateUserRequest req) {
        return ResponseEntity.ok(userService.updateUser(id, req));
    }

    /** PATCH /api/users/:id — used by the user list to reassign a learner's senior. */
    @PatchMapping("/{id}")
    public ResponseEntity<UserDto> patchUser(@PathVariable String id, @RequestBody Map<String, String> patch) {
        String seniorId = patch.get("seniorId");
        boolean clear = patch.containsKey("seniorId") && (seniorId == null || seniorId.isBlank());
        UpdateUserRequest req = new UpdateUserRequest(null, null, null, null, null, clear ? null : seniorId, clear);
        return ResponseEntity.ok(userService.updateUser(id, req));
    }

    /**
     * PATCH /api/users/me/language — the caller's own display language, remembered so notification
     * email can be sent in it.
     *
     * <p>Scoped to the authenticated principal rather than taking an id: this is the one piece of a
     * user record a person should be able to change for themselves, and routing it through the
     * general update endpoint would mean letting them PATCH a user record.
     */
    @PatchMapping("/me/language")
    public ResponseEntity<Void> updateMyLanguage(@RequestBody Map<String, String> body) {
        userService.updatePreferredLanguage(access.actor().getId(), body.get("language"));
        return ResponseEntity.noContent().build();
    }

    /** PATCH /api/users/me/intro  { "version": 1 } — the caller dismissed the intro guide for good. */
    @PatchMapping("/me/intro")
    public ResponseEntity<Void> updateMyIntro(@RequestBody Map<String, Integer> body) {
        userService.updateIntroSeenVersion(access.actor().getId(), body.get("version"));
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/users/:id */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /** Accepts either the numeric code or the English name, so old links keep working. */
    private UserRole parseRole(String role) {
        try {
            return UserRole.fromCode(Integer.parseInt(role.trim()));
        } catch (NumberFormatException notACode) {
            try {
                return UserRole.fromEnglish(role);
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCode.UNKNOWN_CODE, role);
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.UNKNOWN_CODE, role);
        }
    }
}
