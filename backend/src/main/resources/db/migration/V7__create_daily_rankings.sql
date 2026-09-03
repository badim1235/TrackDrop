CREATE TABLE ranking_runs (
    id UUID PRIMARY KEY,
    ranking_date DATE NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failure_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ranking_runs_id_date_unique UNIQUE (id, ranking_date),
    CONSTRAINT ranking_runs_status_check
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ranking_runs_attempt_count_check CHECK (attempt_count >= 0)
);

CREATE TABLE daily_rankings (
    id UUID PRIMARY KEY,
    ranking_run_id UUID NOT NULL,
    ranking_date DATE NOT NULL,
    scope_type VARCHAR(10) NOT NULL,
    genre_id UUID REFERENCES genres (id),
    track_id UUID NOT NULL REFERENCES tracks (id),
    rank BIGINT NOT NULL,
    vote_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT daily_rankings_run_date_fk
        FOREIGN KEY (ranking_run_id, ranking_date)
        REFERENCES ranking_runs (id, ranking_date),
    CONSTRAINT daily_rankings_scope_check
        CHECK (
            (scope_type = 'ALL' AND genre_id IS NULL)
            OR (scope_type = 'GENRE' AND genre_id IS NOT NULL)
        ),
    CONSTRAINT daily_rankings_rank_check CHECK (rank > 0),
    CONSTRAINT daily_rankings_vote_count_check CHECK (vote_count > 0)
);

CREATE UNIQUE INDEX daily_rankings_all_track_unique
    ON daily_rankings (ranking_date, track_id)
    WHERE scope_type = 'ALL';
CREATE UNIQUE INDEX daily_rankings_genre_track_unique
    ON daily_rankings (ranking_date, genre_id, track_id)
    WHERE scope_type = 'GENRE';
CREATE UNIQUE INDEX daily_rankings_all_rank_unique
    ON daily_rankings (ranking_date, rank)
    WHERE scope_type = 'ALL';
CREATE UNIQUE INDEX daily_rankings_genre_rank_unique
    ON daily_rankings (ranking_date, genre_id, rank)
    WHERE scope_type = 'GENRE';

ALTER TABLE ranking_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE daily_rankings ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE ranking_runs, daily_rankings FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE ranking_runs, daily_rankings FROM authenticated;
    END IF;
END
$$;
