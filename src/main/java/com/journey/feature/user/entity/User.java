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

    /**
     * Language the person chose in the UI ("en" / "ar"), used for email. Null until they switch
     * language at least once, in which case notifications fall back to the configured default.
     */
    @Column(name = "preferred_language", length = 8)
    private String preferredLanguage;

    /** Set for learners only — references another User's id. */
    @Column(name = "senior_id", length = 36)
    private String seniorId;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
