ALTER TABLE recommendations
    ADD COLUMN recommended_on DATE;

UPDATE recommendations
SET recommended_on = (created_at AT TIME ZONE 'Asia/Seoul')::DATE;

ALTER TABLE recommendations
    ALTER COLUMN recommended_on SET NOT NULL,
    ALTER COLUMN recommended_on SET DEFAULT ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::DATE),
    DROP CONSTRAINT IF EXISTS recommendations_track_id_key;

ALTER TABLE recommendations
    ADD CONSTRAINT recommendations_track_date_unique UNIQUE (track_id, recommended_on);

CREATE INDEX recommendations_track_recent_idx
    ON recommendations (track_id, recommended_on DESC, created_at DESC, id DESC);
