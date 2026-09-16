package com.journey.feature.learnerJourney.event;

/**
 * Raised when someone leaves a note on a learner journey item.
 *
 * <p>Carries both sides of the conversation so the listener can notify whichever of them did not
 * write it.
 */
public record NoteAddedEvent(
        String learnerJourneyId,
        String journeyTitle,
        String itemTitle,
        String authorId,
        String authorName,
        /** True when the note came from the learner, false when a reviewer wrote it. */
        boolean authorIsLearner,
        String learnerId,
        String assignerId
) {}
