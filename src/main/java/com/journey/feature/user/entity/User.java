package com.journey.feature.user.entity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private Integer role;

    /** Every person belongs to exactly one department. It decides who a Manager can see. */
    @Column(name = "department_id", nullable = false, length = 36)
    private String departmentId;

    /**
     * Language the person chose in the UI ("en" / "ar"), used for email. Null until they switch
     * language at least once, in which case notifications fall back to the configured default.
     */
    @Column(name = "preferred_language", length = 8)
    private String preferredLanguage;

    /** Version of the intro guide this person chose not to see again; null = never dismissed. */
    @Column(name = "intro_seen_version")
    private Integer introSeenVersion;

    /** When the person hid the getting-started checklist; null = still shown. */
    @Column(name = "getting_started_dismissed_at")
    private java.time.LocalDateTime gettingStartedDismissedAt;

    /** Set for learners only — references another User's id. */
    @Column(name = "senior_id", length = 36)
    private String seniorId;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
