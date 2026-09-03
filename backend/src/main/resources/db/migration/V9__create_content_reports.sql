CREATE TABLE content_reports (
    id UUID PRIMARY KEY,
    reporter_user_id UUID NOT NULL REFERENCES users (id),
    recommendation_id UUID NOT NULL REFERENCES recommendations (id),
    reason_code VARCHAR(40) NOT NULL,
    details VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    CONSTRAINT content_reports_reporter_recommendation_unique
        UNIQUE (reporter_user_id, recommendation_id),
    CONSTRAINT content_reports_reason_check
        CHECK (reason_code IN ('ABUSIVE_LANGUAGE', 'SPAM', 'OTHER')),
    CONSTRAINT content_reports_status_check
        CHECK (status IN ('PENDING', 'REVIEWED', 'DISMISSED', 'ACTIONED')),
    CONSTRAINT content_reports_details_length_check
        CHECK (details IS NULL OR char_length(details) <= 500)
);

CREATE INDEX content_reports_status_created_idx
    ON content_reports (status, created_at);

ALTER TABLE content_reports ENABLE ROW LEVEL SECURITY;
