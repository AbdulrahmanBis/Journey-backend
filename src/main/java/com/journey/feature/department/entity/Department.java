package com.journey.feature.department.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An organisational unit people belong to. Departments are data the organisation manages — not a
 * fixed enum — so they carry their own English and Arabic names.
 *
 * <p>They scope people only. Journeys are company-wide.
 */
@Entity
@Table(name = "departments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Department {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "name_en", nullable = false, unique = true, length = 120)
    private String nameEn;

    @Column(name = "name_ar", nullable = false, unique = true, length = 120)
    private String nameAr;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
