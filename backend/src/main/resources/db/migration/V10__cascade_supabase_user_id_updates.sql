ALTER TABLE recommendations
    DROP CONSTRAINT recommendations_recommender_user_id_fkey,
    ADD CONSTRAINT recommendations_recommender_user_id_fkey
        FOREIGN KEY (recommender_user_id) REFERENCES users (id) ON UPDATE CASCADE;

ALTER TABLE votes
    DROP CONSTRAINT votes_user_id_fkey,
    ADD CONSTRAINT votes_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE CASCADE;

ALTER TABLE daily_recommendation_quotas
    DROP CONSTRAINT daily_recommendation_quotas_user_id_fkey,
    ADD CONSTRAINT daily_recommendation_quotas_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE CASCADE;

ALTER TABLE content_reports
    DROP CONSTRAINT content_reports_reporter_user_id_fkey,
    ADD CONSTRAINT content_reports_reporter_user_id_fkey
        FOREIGN KEY (reporter_user_id) REFERENCES users (id) ON UPDATE CASCADE;
