CREATE COLLATION trackdrop_nocase (
    provider = icu,
    locale = 'und-u-ks-level2',
    deterministic = false
);

CREATE TABLE genres (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    sort_order SMALLINT NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO genres (id, code, display_name, sort_order) VALUES
    ('10000000-0000-0000-0000-000000000001', 'hip-hop', 'Hip-Hop', 1),
    ('10000000-0000-0000-0000-000000000002', 'rnb', 'R&B', 2),
    ('10000000-0000-0000-0000-000000000003', 'ballad', 'Ballad', 3),
    ('10000000-0000-0000-0000-000000000004', 'pop', 'Pop', 4),
    ('10000000-0000-0000-0000-000000000005', 'rock', 'Rock', 5),
    ('10000000-0000-0000-0000-000000000006', 'indie', 'Indie', 6),
    ('10000000-0000-0000-0000-000000000007', 'electronic', 'Electronic', 7),
    ('10000000-0000-0000-0000-000000000008', 'jazz', 'Jazz', 8),
    ('10000000-0000-0000-0000-000000000009', 'classical', 'Classical', 9),
    ('10000000-0000-0000-0000-000000000010', 'other', 'Other', 10);
