-- An account created by invitation has no usable password until the person opens the link and chooses one. The admin
-- list shows these as "invited" instead of "active". The flag is cleared when a password is set (invitation link,
-- reset link or an administrator).
ALTER TABLE users ADD COLUMN invitation_pending BOOLEAN NOT NULL DEFAULT FALSE;
