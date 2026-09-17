package com.journey.feature.announcement.repository;

import com.journey.feature.announcement.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, String> {

    List<Announcement> findAllByOrderByCreatedAtDesc();

    boolean existsByAuthorId(String authorId);

    boolean existsByAuthorIdAndOrgWideTrue(String authorId);

    /** Everything that reaches a department: company-wide ones plus those addressed to it. */
    @Query("""
            SELECT DISTINCT a FROM Announcement a LEFT JOIN a.departmentIds d
            WHERE a.orgWide = true OR d = :departmentId
            ORDER BY a.createdAt DESC""")
    List<Announcement> findReaching(@Param("departmentId") String departmentId);

    /** What belongs on this person's dashboard today. */
    @Query("""
            SELECT DISTINCT a FROM Announcement a LEFT JOIN a.departmentIds d
            WHERE (a.orgWide = true OR d = :departmentId)
              AND a.showUntil >= :today
              AND NOT EXISTS (SELECT 1 FROM AnnouncementDismissal x
                              WHERE x.id.announcementId = a.id AND x.id.userId = :userId)
            ORDER BY a.createdAt DESC""")
    List<Announcement> findActiveFor(@Param("departmentId") String departmentId,
                                     @Param("userId") String userId,
                                     @Param("today") LocalDate today);
}
