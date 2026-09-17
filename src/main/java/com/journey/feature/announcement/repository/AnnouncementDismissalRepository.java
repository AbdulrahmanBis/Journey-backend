package com.journey.feature.announcement.repository;

import com.journey.feature.announcement.entity.AnnouncementDismissal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementDismissalRepository extends JpaRepository<AnnouncementDismissal, AnnouncementDismissal.Key> {
}
