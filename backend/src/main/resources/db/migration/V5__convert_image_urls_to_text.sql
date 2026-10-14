-- Convert image and avatar URL columns to TEXT to support Base64 images without length truncation
ALTER TABLE news ALTER COLUMN cover_image_url TYPE TEXT;
ALTER TABLE news ALTER COLUMN author_avatar_url TYPE TEXT;
ALTER TABLE news_sections ALTER COLUMN media_url TYPE TEXT;
ALTER TABLE news_sections ALTER COLUMN link_url TYPE TEXT;
ALTER TABLE users ALTER COLUMN avatar_url TYPE TEXT;
