package com.journey.common.security;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.enums.UserRole;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Arrays;
import java.util.Objects;

/**
 * Every "who may see or change what" rule in the application, in one place.
 *
 * <p>{@code SecurityConfig} only requires that a request is authenticated; it knows nothing about
 * roles or departments. Controllers and services ask this class instead of inspecting authorities
 * themselves, so a rule is written once and cannot drift between the dashboard, the metrics and
 * the journey log.
 *
 * <h2>Who can see whom</h2>
 * <ul>
 *   <li><b>Admin, HR</b> — everyone, in every department.</li>
 *   <li><b>Manager</b> — everyone in their own department.</li>
 *   <li><b>Senior</b> — themselves and the learners assigned to them.</li>
 *   <li><b>Learner</b> — themselves.</li>
 * </ul>
 * Departments scope <em>people</em>. Journeys are company-wide and visible to everyone.
 *
 * <h2>Why the database and not the token</h2>
 * The caller's role and department are read from {@code users} on each request, not from the JWT.
 * A token lives for hours; moving someone to another department, or demoting them, must take
 * effect immediately rather than when their token happens to expire. The lookup is cached for the
 * rest of the request.
 */
@Component
@RequiredArgsConstructor
public class AccessPolicy {

    /** Roles that may author journeys and exams, assign them, and review a learner's work. */
    public static final UserRole[] STAFF = { UserRole.SENIOR, UserRole.MANAGER, UserRole.HR, UserRole.ADMIN };

    /** Roles that may manage user accounts at all (a Manager only within their department). */
    public static final UserRole[] USER_ADMINS = { UserRole.MANAGER, UserRole.HR, UserRole.ADMIN };

    private static final String ACTOR_ATTRIBUTE = AccessPolicy.class.getName() + ".actor";

    private final UserRepository userRepository;

    // ─── The caller ─────────────────────────────────────────────────────────────────────

    /** The signed-in person, as they are in the database right now. */
    public User actor() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null) {
            Object cached = request.getAttribute(ACTOR_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof User user) return user;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED);
        }
        User user = userRepository.findById(auth.getName())
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_GONE));

        if (request != null) request.setAttribute(ACTOR_ATTRIBUTE, user, RequestAttributes.SCOPE_REQUEST);
        return user;
    }

    public static UserRole roleOf(User user) {
        return UserRole.fromCode(user.getRole());
    }

    public static boolean hasRole(User user, UserRole... roles) {
        UserRole role = roleOf(user);
        return Arrays.asList(roles).contains(role);
    }

    /** Admin and HR see across departments. */
    public static boolean isOrgWide(User user) {
        return hasRole(user, UserRole.ADMIN, UserRole.HR);
    }

    /** The caller, provided they hold one of these roles; 403 otherwise. */
    public User requireRole(UserRole... roles) {
        User actor = actor();
        if (!hasRole(actor, roles)) {
            throw new ApiException(ErrorCode.ROLE_NOT_ALLOWED);
        }
        return actor;
    }

    // ─── People ─────────────────────────────────────────────────────────────────────────

    public boolean canView(User actor, User target) {
        if (actor.getId().equals(target.getId())) return true;
        if (isOrgWide(actor)) return true;
        if (hasRole(actor, UserRole.MANAGER)) return sameDepartment(actor, target);
        if (hasRole(actor, UserRole.SENIOR)) return actor.getId().equals(target.getSeniorId());
        return false;
    }

    /**
     * The person with this id, if the caller may see them.
     *
     * <p>404 when they do not exist, 403 when they do but are out of reach. Both are fine to
     * reveal here: every caller who gets this far is an authenticated colleague, and a 403 is far
     * easier to diagnose than a record that silently "does not exist".
     */
    public User requireViewable(String userId) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (!canView(actor(), target)) {
            throw new ApiException(ErrorCode.PERSON_NOT_VISIBLE);
        }
        return target;
    }

    /**
     * Someone who may act on a learner's work as a reviewer — change their progress on their
     * behalf, override a journey's status, grade their exam. Never the learner themselves.
     */
    public boolean isReviewerOf(User actor, User learner) {
        return !actor.getId().equals(learner.getId())
                && hasRole(actor, STAFF)
                && canView(actor, learner);
    }

    // ─── Account management ───────────────────────────────────────────────────────────────

    /**
     * May the caller edit or delete this account?
     *
     * <p>Admin: anyone. HR: anyone except an Admin. Manager: people in their own department who
     * are not Admin or HR — otherwise a Manager could lock out the very people above them.
     */
    public boolean canManageAccount(User actor, User target) {
        if (hasRole(actor, UserRole.ADMIN)) return true;
        if (hasRole(actor, UserRole.HR)) return !hasRole(target, UserRole.ADMIN);
        if (hasRole(actor, UserRole.MANAGER)) {
            return sameDepartment(actor, target) && !hasRole(target, UserRole.ADMIN, UserRole.HR);
        }
        return false;
    }

    public void requireCanManageAccount(User target) {
        if (!canManageAccount(actor(), target)) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_MANAGEABLE);
        }
    }

    /**
     * May the caller give someone this role in this department? Guards against privilege
     * escalation: nobody can hand out a role above their own reach.
     *
     * <ul>
     *   <li>Admin: any role, any department.</li>
     *   <li>HR: any role except Admin, any department.</li>
     *   <li>Manager: Manager, Senior or Learner — in their own department only.</li>
     * </ul>
     */
    public void requireCanAssign(UserRole role, String departmentId) {
        User actor = actor();
        if (hasRole(actor, UserRole.ADMIN)) return;
        if (hasRole(actor, UserRole.HR)) {
            if (role == UserRole.ADMIN) throw new ApiException(ErrorCode.GRANT_ADMIN_ONLY);
            return;
        }
        if (hasRole(actor, UserRole.MANAGER)) {
            if (role == UserRole.ADMIN || role == UserRole.HR) {
                throw new ApiException(ErrorCode.GRANT_ROLE_NOT_ALLOWED, ApiException.text(role.getEnglish(), role.getArabic()));
            }
            if (!Objects.equals(departmentId, actor.getDepartmentId())) {
                throw new ApiException(ErrorCode.OWN_DEPARTMENT_ONLY);
            }
            return;
        }
        throw new ApiException(ErrorCode.ROLE_NOT_ALLOWED);
    }

    // ─── Department-scoped views ────────────────────────────────────────────────────────────

    /**
     * Resolves which department an organisation-level view (dashboard tree, org metrics, user
     * list) should cover.
     *
     * @param requested the department the client asked for, or null for "all"
     * @return a department id, or null meaning every department
     * @throws ApiException 403 for a Manager asking about another department, and for
     *                                 roles that have no organisation-level view at all
     */
    public String departmentScope(String requested) {
        User actor = actor();
        if (isOrgWide(actor)) {
            return blankToNull(requested);
        }
        if (hasRole(actor, UserRole.MANAGER)) {
            String own = actor.getDepartmentId();
            if (requested != null && !requested.isBlank() && !requested.equals(own)) {
                throw new ApiException(ErrorCode.OWN_DEPARTMENT_ONLY);
            }
            return own;
        }
        throw new ApiException(ErrorCode.NO_ORGANISATION_VIEW);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────────────

    private static boolean sameDepartment(User a, User b) {
        return a.getDepartmentId() != null && a.getDepartmentId().equals(b.getDepartmentId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
