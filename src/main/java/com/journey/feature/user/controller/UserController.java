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
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** GET /api/users?role=&seniorId= */
    @GetMapping
    public ResponseEntity<List<UserDto>> getUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String seniorId) {

        if (role != null && seniorId != null) {
            return ResponseEntity.ok(userService.getLearnersBySenior(seniorId));
        }
        if (role != null) {
            //TODO: fix userrole
            UserRole userRole = Arrays.stream(UserRole.values())
                    .filter(r -> r.getEnglish().equalsIgnoreCase(role))
                    .findFirst()
                    .orElseThrow();

            return ResponseEntity.ok(userService.getUsersByRole(userRole));
    }
        return ResponseEntity.ok(userService.getAllUsers());
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
    public ResponseEntity<UserDto> updateUser(@PathVariable String id,
                                              @RequestBody UpdateUserRequest req) {
        return ResponseEntity.ok(userService.updateUser(id, req));
    }

    /** PATCH /api/users/:id  — used by Angular to reassign learner's senior */
    @PatchMapping("/{id}")
    public ResponseEntity<UserDto> patchUser(@PathVariable String id,
                                             @RequestBody Map<String, String> patch) {
        UpdateUserRequest req = new UpdateUserRequest(null, null, null, null, patch.get("seniorId"));
        return ResponseEntity.ok(userService.updateUser(id, req));
    }

    /** DELETE /api/users/:id */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
