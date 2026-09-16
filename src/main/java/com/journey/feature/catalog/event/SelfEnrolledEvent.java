package com.journey.feature.catalog.event;

/**
 * A learner enrolled themselves from the catalog. Values, not entities: the listener runs after
 * commit, possibly on another thread.
 *
 * @param targetPath where the reviewer should go: the journey log, or the team dashboard
 */
public record SelfEnrolledEvent(
        String learnerId,
        String learnerName,
        String reviewerId,
        String title,
        String typeEnglish,
        String typeArabic,
        String targetPath
) {}
