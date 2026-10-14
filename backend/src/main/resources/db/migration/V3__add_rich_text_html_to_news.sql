-- Add Rich Text HTML content columns to news table
ALTER TABLE news ADD COLUMN content_html_en TEXT;
ALTER TABLE news ADD COLUMN content_html_fr TEXT;
ALTER TABLE news ADD COLUMN content_html_zh TEXT;
