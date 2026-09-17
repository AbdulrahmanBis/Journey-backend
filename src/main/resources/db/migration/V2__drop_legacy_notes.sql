-- The old per-item `notes` table was replaced by journey_notes; some databases still carry it.
DROP TABLE IF EXISTS notes;
