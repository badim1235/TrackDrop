ALTER TABLE users DROP COLUMN login_id;
ALTER TABLE users DROP COLUMN password_hash;

COMMENT ON COLUMN users.id IS 'Supabase Auth user UUID';
COMMENT ON COLUMN users.email IS 'Verified email managed by Supabase Auth';
