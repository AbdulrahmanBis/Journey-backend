package com.journey.feature.announcement.service;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.enums.UserRole;
import com.journey.common.security.AccessPolicy;
import com.journey.common.service.IdGeneratorService;
import com.journey.feature.announcement.dto.AnnouncementDto;
import com.journey.feature.announcement.dto.SaveAnnouncementRequest;
import com.journey.feature.announcement.entity.Announcement;
import com.journey.feature.announcement.entity.AnnouncementDismissal;
import com.journey.feature.announcement.event.AnnouncementDeletedEvent;
import com.journey.feature.announcement.event.AnnouncementPublishedEvent;
import com.journey.feature.announcement.repository.AnnouncementDismissalRepository;
import com.journey.feature.announcement.repository.AnnouncementRepository;
import com.journey.feature.department.dto.DepartmentDto;
import com.journey.feature.department.entity.Department;
import com.journey.feature.department.repository.DepartmentRepository;
import com.journey.feature.user.entity.User;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Announcements: who may post to whom, who sees what, and who hears about it.
 *
 * <ul>
 *   <li><b>Post</b> — HR and Admin to everyone or chosen departments; a Manager to their own department only.</li>
 *   <li><b>Edit / delete</b> — HR and Admin any announcement; a Manager only their own.</li>
 *   <li><b>Announcements page</b> — HR and Admin see all (optionally one department's); a Manager sees
 *       what reaches their department.</li>
 *   <li><b>Dashboard</b> — everyone sees what reaches their department until its show-until date or
 *       until they dismiss it.</li>
 * </ul>
 * Posting notifies the audience (in-app and email); the author is not notified of their own post.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    static final int DEFAULT_SHOW_DAYS = 14;
    static final int MAX_SHOW_DAYS = 365;
    static final int EXCERPT_LENGTH = 160;

    private static final UserRole[] AUTHORS = { UserRole.MANAGER, UserRole.HR, UserRole.ADMIN };
    private static final Pattern BLOCK_END = Pattern.compile("(?i)</(p|li|h[1-6]|div|blockquote|pre)>|<br\\s*/?>");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    private final AnnouncementRepository announcements;
    private final AnnouncementDismissalRepository dismissals;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final IdGeneratorService idGenerator;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;

    // ─── Reading ────────────────────────────────────────────────────────────────────────

    /** The announcements page. */
    @Transactional(readOnly = true)
    public List<AnnouncementDto> list(String departmentId) {
        User actor = access.requireRole(AUTHORS);
        String scope = access.departmentScope(departmentId);
        List<Announcement> found = scope == null
                ? announcements.findAllByOrderByCreatedAtDesc()
                : announcements.findReaching(scope);
        return toDtos(found, actor);
    }

    /** The top of the caller's dashboard. */
    @Transactional(readOnly = true)
    public List<AnnouncementDto> active() {
        User actor = access.actor();
        return toDtos(announcements.findActiveFor(actor.getDepartmentId(), actor.getId(), LocalDate.now()), actor);
    }

    /** One announcement, for a notification link — to its audience, and to whoever could see it on the page. */
    @Transactional(readOnly = true)
    public AnnouncementDto get(String id) {
        User actor = access.actor();
        Announcement a = findOrThrow(id);
        if (!canRead(actor, a)) throw new ApiException(ErrorCode.ANNOUNCEMENT_NOT_ADDRESSED);
        return toDtos(List.of(a), actor).get(0);
    }

    @Transactional
    public void dismiss(String id) {
        User actor = access.actor();
        Announcement a = findOrThrow(id);
        if (!a.reaches(actor.getDepartmentId())) throw new ApiException(ErrorCode.ANNOUNCEMENT_NOT_ADDRESSED);
        AnnouncementDismissal.Key key = new AnnouncementDismissal.Key(id, actor.getId());
        if (!dismissals.existsById(key)) dismissals.save(new AnnouncementDismissal(id, actor.getId()));
    }

    // ─── Writing ────────────────────────────────────────────────────────────────────────

    @Transactional
    public AnnouncementDto create(SaveAnnouncementRequest req) {
        User actor = access.requireRole(AUTHORS);
        Announcement a = Announcement.builder()
                .authorId(actor.getId())
                .authorName(actor.getName())
                .build();
        apply(a, req, actor);
        a.setId(idGenerator.next(IdGeneratorService.ANNOUNCEMENT, "ann-"));
        announcements.save(a);
        publish(a, actor);
        return toDtos(List.of(a), actor).get(0);
    }

    @Transactional
    public AnnouncementDto update(String id, SaveAnnouncementRequest req) {
        User actor = access.requireRole(AUTHORS);
        Announcement a = findOrThrow(id);
        if (!canEdit(actor, a)) throw new ApiException(ErrorCode.ANNOUNCEMENT_OWN_ONLY);
        apply(a, req, actor);
        a.setUpdatedAt(LocalDateTime.now());
        announcements.save(a);
        if (Boolean.TRUE.equals(req.notifyAgain())) publish(a, actor);
        return toDtos(List.of(a), actor).get(0);
    }

    @Transactional
    public void delete(String id) {
        User actor = access.requireRole(AUTHORS);
        Announcement a = findOrThrow(id);
        if (!canEdit(actor, a)) throw new ApiException(ErrorCode.ANNOUNCEMENT_OWN_ONLY);
        announcements.delete(a);
        events.publishEvent(new AnnouncementDeletedEvent(id));
    }

    /** Validates the request against the caller's reach and copies it onto the announcement. */
    private void apply(Announcement a, SaveAnnouncementRequest req, User actor) {
        String title = req.title().trim();
        if (title.isEmpty()) throw new ApiException(ErrorCode.ANNOUNCEMENT_TITLE_REQUIRED);
        if (plainText(req.body()).isEmpty()) throw new ApiException(ErrorCode.ANNOUNCEMENT_BODY_REQUIRED);

        LocalDate today = LocalDate.now();
        LocalDate showUntil = req.showUntil() == null ? today.plusDays(DEFAULT_SHOW_DAYS) : req.showUntil();
        // An edit may keep a date that has since passed; a new date must not be in the past.
        boolean unchanged = showUntil.equals(a.getShowUntil());
        if (!unchanged && showUntil.isBefore(today)) throw new ApiException(ErrorCode.ANNOUNCEMENT_SHOW_UNTIL_PAST);
        if (showUntil.isAfter(today.plusDays(MAX_SHOW_DAYS))) throw new ApiException(ErrorCode.ANNOUNCEMENT_SHOW_UNTIL_TOO_FAR);

        Set<String> departments = new LinkedHashSet<>(req.departmentIds() == null ? List.of() : req.departmentIds());
        departments.removeIf(d -> d == null || d.isBlank());
        boolean orgWide = Boolean.TRUE.equals(req.orgWide());

        if (AccessPolicy.isOrgWide(actor)) {
            if (orgWide) {
                departments.clear();
            } else if (departments.isEmpty()) {
                throw new ApiException(ErrorCode.ANNOUNCEMENT_AUDIENCE_REQUIRED);
            } else {
                for (String d : departments) {
                    if (!departmentRepository.existsById(d)) throw new ApiException(ErrorCode.UNKNOWN_DEPARTMENT);
                }
            }
        } else {
            String own = actor.getDepartmentId();
            if (orgWide || departments.stream().anyMatch(d -> !d.equals(own))) {
                throw new ApiException(ErrorCode.OWN_DEPARTMENT_ONLY);
            }
            departments = new LinkedHashSet<>(List.of(own));
        }

        a.setTitle(title);
        a.setBody(req.body());
        a.setOrgWide(orgWide);
        a.getDepartmentIds().clear();
        a.getDepartmentIds().addAll(departments);
        a.setShowUntil(showUntil);
    }

    private void publish(Announcement a, User author) {
        List<User> audience = a.isOrgWide()
                ? userRepository.findAll()
                : userRepository.findByDepartmentIdIn(a.getDepartmentIds());
        List<String> recipientIds = audience.stream()
                .map(User::getId)
                .filter(id -> !id.equals(author.getId()))
                .toList();
        if (recipientIds.isEmpty()) return;
        events.publishEvent(new AnnouncementPublishedEvent(a.getId(), a.getTitle(), excerpt(a.getBody()),
                author.getName(), recipientIds));
    }

    // ─── Rules ──────────────────────────────────────────────────────────────────────────

    private boolean canEdit(User actor, Announcement a) {
        if (AccessPolicy.isOrgWide(actor)) return true;
        return AccessPolicy.hasRole(actor, UserRole.MANAGER) && actor.getId().equals(a.getAuthorId());
    }

    private boolean canRead(User actor, Announcement a) {
        if (a.reaches(actor.getDepartmentId())) return true;
        if (AccessPolicy.isOrgWide(actor)) return true;
        return AccessPolicy.hasRole(actor, UserRole.MANAGER) && actor.getId().equals(a.getAuthorId());
    }

    // ─── Mapping ────────────────────────────────────────────────────────────────────────

    private List<AnnouncementDto> toDtos(List<Announcement> list, User actor) {
        Set<String> ids = list.stream().flatMap(a -> a.getDepartmentIds().stream()).collect(Collectors.toSet());
        Map<String, Department> byId = departmentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
        LocalDate today = LocalDate.now();
        return list.stream().map(a -> new AnnouncementDto(
                a.getId(),
                a.getTitle(),
                a.getBody(),
                excerpt(a.getBody()),
                a.isOrgWide(),
                a.getDepartmentIds().stream()
                        .map(byId::get)
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(Department::getNameEn))
                        .map(d -> new DepartmentDto(d.getId(), d.getNameEn(), d.getNameAr(), null))
                        .toList(),
                a.getShowUntil(),
                !a.getShowUntil().isBefore(today),
                a.getAuthorId(),
                a.getAuthorName(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                canEdit(actor, a)
        )).toList();
    }

    /** The body as one line of plain text. */
    static String plainText(String html) {
        if (html == null) return "";
        String text = TAG.matcher(BLOCK_END.matcher(html).replaceAll(" ")).replaceAll("");
        text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        return text.replaceAll("\\s+", " ").trim();
    }

    /** The first {@value #EXCERPT_LENGTH} characters of the body, cut at a word. */
    static String excerpt(String html) {
        String text = plainText(html);
        if (text.length() <= EXCERPT_LENGTH) return text;
        int cut = text.lastIndexOf(' ', EXCERPT_LENGTH);
        return text.substring(0, cut > EXCERPT_LENGTH / 2 ? cut : EXCERPT_LENGTH).trim() + "…";
    }

    private Announcement findOrThrow(String id) {
        return announcements.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
    }
}
