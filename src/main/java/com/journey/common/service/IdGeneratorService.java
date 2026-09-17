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
 * <p>Concurrency: reading MAX(id) alone races — two inserts that haven't committed yet both see the
 * same maximum (notifications dispatched in parallel hit this). So the service also remembers the last
 * number it issued per prefix and never goes below it, under a lock. That covers everything inside this
 * JVM. Several backend instances writing to one database would still need a sequence table.
 */
@Service
@RequiredArgsConstructor
public class IdGeneratorService {

    @PersistenceContext
    private EntityManager entityManager;

    /** Highest number handed out per table and prefix, including rows not committed yet. */
    private final java.util.Map<String, Long> lastIssued = new java.util.HashMap<>();

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
    public static final String NOTIFICATION = "notifications";
    public static final String DEPARTMENT = "departments";
    public static final String PACKAGE = "packages";
    public static final String PACKAGE_ASSIGNMENT = "package_assignments";
    public static final String JOURNEY_UNIT = "journey_units";
    public static final String UNIT_QUIZ_QUESTION = "unit_quiz_questions";
    public static final String LEARNER_JOURNEY_UNIT = "learner_journey_units";

    /**
     * @param table  physical table name — must be one of the constants above (never user input;
     *               it is interpolated into the query because table names cannot be bound)
     * @param prefix id prefix including the dash, e.g. {@code "u-"}
     */
    public synchronized String next(String table, String prefix) {
        assertKnownTable(table);

        Object max = entityManager.createNativeQuery(
                        "SELECT MAX(CAST(SUBSTRING(id, :len) AS UNSIGNED)) FROM " + table +
                                " WHERE id REGEXP :pattern")
                .setParameter("len", prefix.length() + 1)
                .setParameter("pattern", "^" + prefix + "[0-9]+$")
                .getSingleResult();

        long fromDb = max == null ? 0L : ((Number) max).longValue();
        String key = table + ':' + prefix;
        long nextNumber = Math.max(fromDb, lastIssued.getOrDefault(key, 0L)) + 1L;
        lastIssued.put(key, nextNumber);
        return prefix + nextNumber;
    }

    /** Guards the interpolated table name against anything not on the known list. */
    private void assertKnownTable(String table) {
        switch (table) {
            case USER, JOURNEY, JOURNEY_ITEM, JOURNEY_ITEM_ATTACHMENT, LEARNER_JOURNEY,
                 LEARNER_JOURNEY_ITEM, NOTE, EXAM, EXAM_QUESTION, EXAM_ATTEMPT,
                 NOTIFICATION, DEPARTMENT, PACKAGE, PACKAGE_ASSIGNMENT,
                 JOURNEY_UNIT, UNIT_QUIZ_QUESTION, LEARNER_JOURNEY_UNIT -> { /* ok */ }
            default -> throw new IllegalArgumentException("Unknown table for id generation: " + table);
        }
    }
}
