-- V11__add_parent_type_to_categories.sql
ALTER TABLE categories ADD COLUMN IF NOT EXISTS parent_type VARCHAR(50) NOT NULL DEFAULT 'two-wheeler';

CREATE INDEX IF NOT EXISTS idx_categories_parent_type ON categories(parent_type);

-- Update existing default categories to their appropriate parent_type
UPDATE categories SET parent_type = 'three-wheeler' WHERE slug IN ('tricycle', 'cargo-tricycle', 'passenger-tricycle');
UPDATE categories SET parent_type = 'atv' WHERE slug IN ('atv', 'quad-atv');
UPDATE categories SET parent_type = 'two-wheeler' WHERE slug IN ('street', 'offroad', 'underbone', 'scooter', 'sport', 'touring', 'standard');
