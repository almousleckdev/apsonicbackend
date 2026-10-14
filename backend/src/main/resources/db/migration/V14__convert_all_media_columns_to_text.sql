-- V14__convert_all_media_columns_to_text.sql
-- Convert all product and category image/media URL columns to TEXT to avoid string length limits and truncation errors

-- 1. Categories
ALTER TABLE categories ALTER COLUMN image_url TYPE TEXT;

-- 2. Products
ALTER TABLE products ALTER COLUMN cover_image_url TYPE TEXT;
ALTER TABLE products ALTER COLUMN hero_image_url TYPE TEXT;
ALTER TABLE products ALTER COLUMN performance_image_url TYPE TEXT;
ALTER TABLE products ALTER COLUMN story_background_image TYPE TEXT;
ALTER TABLE products ALTER COLUMN aesthetic_bg_image TYPE TEXT;
ALTER TABLE products ALTER COLUMN aesthetic_image_1 TYPE TEXT;
ALTER TABLE products ALTER COLUMN aesthetic_image_2 TYPE TEXT;

-- 3. Product Colors
ALTER TABLE product_colors ALTER COLUMN image_url TYPE TEXT;

-- 4. Product Spec Banners
ALTER TABLE product_spec_banners ALTER COLUMN image_url TYPE TEXT;
