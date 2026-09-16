package com.journey.common.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Generates short, readable, sequential ids (u-1, lj-15, ex-3) instead of UUIDs.
 *
 * <p>The seeded data already used this convention (u-mgr, lj-3, lji-2-4, ex-ng), but rows created
 * through the app got 36-char UUIDs, so the tables ended up with two styles side by side. Ids stay
 * {@code varchar(36)} and existing rows are untouched — only newly created rows change.
 *
 * <p>The next number is the highest existing {@code <prefix><digits>} id plus one. Ids that don't
 * match that exact shape (the seeded {@code ji-aws-1} / {@code eq-aws-1} style, or old UUIDs) are
 * ignored by the pattern, so they can never collide with what we generate.
 *
 * <p>Note: this is a read-then-write, so two concurrent inserts into the same table could race for
 * the same number. At this application's scale that is acceptable; if it ever matters, move to a
 * dedicated sequence table or a DB sequence.
 */
@Service
@RequiredArgsConstructor
public class IdGeneratorService {

    @PersistenceContext
    private EntityManager entityManager;

    public static final String USER = "users";
    public static final String JOURNEY = "journeys";
    public static final String JOURNEY_ITEM = "journey_items";
    public static final String JOURNEY_ITEM_ATTACHMENT = "journey_item_attachments";
    public static final String LEARNER_JOURNEY = "learner_journeys";
    public static final String LEARNER_JOURNEY_ITEM = "learner_journey_items";
    public static final String NOTE = "journey_notes";
    public static final String EXAM = "exams";
    public static final String EXAM_QUESTION = "exam_questions";
    public static final String EXAM_ATTEMPT = "exam_attempts";

    /**
     * @param table  physical table name — must be one of the constants above (never user input;
     *               it is interpolated into the query because table names cannot be bound)
     * @param prefix id prefix including the dash, e.g. {@code "u-"}
     */
    public String next(String table, String prefix) {
        assertKnownTable(table);

        Object max = entityManager.createNativeQuery(
                        "SELECT MAX(CAST(SUBSTRING(id, :len) AS UNSIGNED)) FROM " + table +
                                " WHERE id REGEXP :pattern")
                .setParameter("len", prefix.length() + 1)
                .setParameter("pattern", "^" + prefix + "[0-9]+$")
                .getSingleResult();

        long nextNumber = (max == null ? 0L : ((Number) max).longValue()) + 1L;
        return prefix + nextNumber;
    }

    /** Guards the interpolated table name against anything not on the known list. */
    private void assertKnownTable(String table) {
        switch (table) {
            case USER, JOURNEY, JOURNEY_ITEM, JOURNEY_ITEM_ATTACHMENT, LEARNER_JOURNEY,
                 LEARNER_JOURNEY_ITEM, NOTE, EXAM, EXAM_QUESTION, EXAM_ATTEMPT -> { /* ok */ }
            default -> throw new IllegalArgumentException("Unknown table for id generation: " + table);
        }
    }
}
