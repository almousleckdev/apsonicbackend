-- A deleted account must not keep its email address or username reserved: creating a new account with them failed
-- with a database error because the unique rule also counted soft-deleted rows. The rule now covers active rows only.
ALTER TABLE users DROP CONSTRAINT users_email_key;
ALTER TABLE users DROP CONSTRAINT users_username_key;
CREATE UNIQUE INDEX users_email_active_key ON users (email) WHERE deleted IS NOT TRUE;
CREATE UNIQUE INDEX users_username_active_key ON users (username) WHERE deleted IS NOT TRUE;
