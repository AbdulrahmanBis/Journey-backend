package com.journey.feature.announcement.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A message to everyone ({@code orgWide}) or to the people in some departments. It shows at the top of
 * their dashboards until {@code showUntil}, unless they dismiss it first.
 */
@Entity
@Table(name = "announcements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Announcement {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 200)
    private String title;

    /** Rich HTML from the editor. Rendered by the browser's sanitizer, never inserted into email. */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String body;

    @Column(name = "org_wide", nullable = false)
    private boolean orgWide;

    /** Empty when {@link #orgWide}. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "announcement_departments", joinColumns = @JoinColumn(name = "announcement_id"))
    @Column(name = "department_id", length = 36)
    @Builder.Default
    private Set<String> departmentIds = new LinkedHashSet<>();

    /** Last day it shows on dashboards (inclusive). */
    @Column(name = "show_until", nullable = false)
    private LocalDate showUntil;

    @Column(name = "author_id", length = 36)
    private String authorId;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Whether it reaches someone in this department. */
    public boolean reaches(String departmentId) {
        return orgWide || (departmentId != null && departmentIds.contains(departmentId));
    }
}
