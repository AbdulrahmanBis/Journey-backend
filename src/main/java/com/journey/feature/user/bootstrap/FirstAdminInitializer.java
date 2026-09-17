package com.journey.feature.user.bootstrap;

import com.journey.common.enums.UserRole;
import com.journey.common.service.IdGeneratorService;
import com.journey.feature.department.entity.Department;
import com.journey.feature.department.repository.DepartmentRepository;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes a brand-new database usable: accounts are only ever created by HR, a Manager or an Admin, so an empty
 * {@code users} table would lock everyone out.
 *
 * <p>Runs once at startup and does nothing unless no Admin exists <em>and</em> {@code app.bootstrap.admin.email}
 * and {@code app.bootstrap.admin.password} are both set. It never touches an existing account.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirstAdminInitializer {

    private final UserRepository users;
    private final DepartmentRepository departments;
    private final IdGeneratorService idGenerator;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.email:}")
    private String email;

    @Value("${app.bootstrap.admin.password:}")
    private String password;

    @Value("${app.bootstrap.admin.name:Administrator}")
    private String name;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createFirstAdmin() {
        if (!users.findByRole(UserRole.ADMIN.getCode()).isEmpty()) return;
        if (email.isBlank() || password.isBlank()) {
            log.warn("No Admin account exists. Set app.bootstrap.admin.email and app.bootstrap.admin.password "
                    + "(BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD in Docker) to create one at startup.");
            return;
        }
        if (password.length() < 8) {
            log.error("app.bootstrap.admin.password must be at least 8 characters; no Admin was created.");
            return;
        }
        String normalizedEmail = email.trim();
        if (users.findByEmail(normalizedEmail).isPresent()) {
            log.error("Cannot create the first Admin: {} already belongs to a non-Admin account.", normalizedEmail);
            return;
        }

        Department department = departments.findAll().stream().findFirst().orElseGet(() ->
                departments.save(Department.builder()
                        .id(idGenerator.next(IdGeneratorService.DEPARTMENT, "dep-"))
                        .nameEn("Administration")
                        .nameAr("الإدارة")
                        .build()));

        users.save(User.builder()
                .id(idGenerator.next(IdGeneratorService.USER, "u-"))
                .name(name.isBlank() ? "Administrator" : name.trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(password))
                .role(UserRole.ADMIN.getCode())
                .departmentId(department.getId())
                .build());
        log.info("Created the first Admin account ({}). Change its password after signing in.", normalizedEmail);
    }
}
