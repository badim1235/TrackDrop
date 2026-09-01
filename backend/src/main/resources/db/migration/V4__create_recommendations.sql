DELETE FROM genres;

INSERT INTO genres (id, code, display_name, sort_order) VALUES
    ('10000000-0000-0000-0000-000000000001', 'alternative', 'Alternative', 10),
    ('10000000-0000-0000-0000-000000000002', 'blues', 'Blues', 20),
    ('10000000-0000-0000-0000-000000000003', 'childrens-music', 'Children''s Music', 30),
    ('10000000-0000-0000-0000-000000000004', 'christian-gospel', 'Christian & Gospel', 40),
    ('10000000-0000-0000-0000-000000000005', 'classical', 'Classical', 50),
    ('10000000-0000-0000-0000-000000000006', 'comedy', 'Comedy', 60),
    ('10000000-0000-0000-0000-000000000007', 'country', 'Country', 70),
    ('10000000-0000-0000-0000-000000000008', 'dance', 'Dance', 80),
    ('10000000-0000-0000-0000-000000000009', 'electronic', 'Electronic', 90),
    ('10000000-0000-0000-0000-000000000010', 'fitness-workout', 'Fitness & Workout', 100),
    ('10000000-0000-0000-0000-000000000011', 'hip-hop-rap', 'Hip-Hop/Rap', 110),
    ('10000000-0000-0000-0000-000000000012', 'jazz', 'Jazz', 120),
    ('10000000-0000-0000-0000-000000000013', 'k-pop', 'K-Pop', 130),
    ('10000000-0000-0000-0000-000000000014', 'j-pop', 'J-Pop', 140),
    ('10000000-0000-0000-0000-000000000015', 'latino', 'Latino', 150),
    ('10000000-0000-0000-0000-000000000016', 'metal', 'Metal', 160),
    ('10000000-0000-0000-0000-000000000017', 'pop', 'Pop', 170),
    ('10000000-0000-0000-0000-000000000018', 'rnb-soul', 'R&B/Soul', 180),
    ('10000000-0000-0000-0000-000000000019', 'reggae', 'Reggae', 190),
    ('10000000-0000-0000-0000-000000000020', 'rock', 'Rock', 200),
    ('10000000-0000-0000-0000-000000000021', 'singer-songwriter', 'Singer/Songwriter', 210),
    ('10000000-0000-0000-0000-000000000022', 'soundtrack', 'Soundtrack', 220),
    ('10000000-0000-0000-0000-000000000023', 'world', 'World', 230),
    ('10000000-0000-0000-0000-000000000024', 'other', 'Other', 240);

CREATE TABLE tracks (
    id UUID PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    artist_name VARCHAR(500) NOT NULL,
    album_name VARCHAR(500),
    album_cover_url TEXT,
    release_year SMALLINT,
    isrc VARCHAR(20),
    explicit BOOLEAN NOT NULL DEFAULT FALSE,
    provider_genre_name VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX tracks_isrc_idx ON tracks (isrc) WHERE isrc IS NOT NULL;

CREATE TABLE track_provider_refs (
    id UUID PRIMARY KEY,
    track_id UUID NOT NULL REFERENCES tracks (id),
    provider VARCHAR(30) NOT NULL,
    external_track_id VARCHAR(255) NOT NULL,
    external_url TEXT,
    preview_url TEXT,
    metadata_refreshed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT track_provider_refs_provider_check CHECK (provider IN ('APPLE_MUSIC')),
    CONSTRAINT track_provider_refs_provider_external_unique UNIQUE (provider, external_track_id),
    CONSTRAINT track_provider_refs_track_provider_unique UNIQUE (track_id, provider)
);

CREATE TABLE track_genres (
    track_id UUID NOT NULL REFERENCES tracks (id),
    genre_id UUID NOT NULL REFERENCES genres (id),
    source VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (track_id, genre_id),
    CONSTRAINT track_genres_source_check CHECK (source IN ('USER_SELECTED', 'PROVIDER', 'CURATED'))
);

CREATE TABLE recommendations (
    id UUID PRIMARY KEY,
    recommender_user_id UUID NOT NULL REFERENCES users (id),
    track_id UUID NOT NULL UNIQUE REFERENCES tracks (id),
    primary_genre_id UUID NOT NULL REFERENCES genres (id),
    comment VARCHAR(120) NOT NULL,
    comment_visibility VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT recommendations_track_genre_fk
        FOREIGN KEY (track_id, primary_genre_id)
        REFERENCES track_genres (track_id, genre_id),
    CONSTRAINT recommendations_comment_length_check
        CHECK (char_length(btrim(comment)) BETWEEN 1 AND 120),
    CONSTRAINT recommendations_visibility_check
        CHECK (comment_visibility IN ('VISIBLE', 'HIDDEN'))
);

CREATE INDEX recommendations_genre_track_idx
    ON recommendations (primary_genre_id, track_id);
CREATE INDEX recommendations_created_idx
    ON recommendations (created_at DESC, id DESC);

CREATE TABLE votes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    track_id UUID NOT NULL REFERENCES tracks (id),
    voted_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT votes_user_date_track_unique UNIQUE (user_id, voted_on, track_id)
);

CREATE INDEX votes_date_track_idx ON votes (voted_on, track_id);
CREATE INDEX votes_user_date_idx ON votes (user_id, voted_on);

CREATE TABLE daily_recommendation_quotas (
    user_id UUID NOT NULL REFERENCES users (id),
    quota_date DATE NOT NULL,
    daily_limit SMALLINT NOT NULL,
    used_count SMALLINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, quota_date),
    CONSTRAINT daily_quotas_limit_check CHECK (daily_limit > 0),
    CONSTRAINT daily_quotas_used_check CHECK (used_count BETWEEN 0 AND daily_limit)
);
