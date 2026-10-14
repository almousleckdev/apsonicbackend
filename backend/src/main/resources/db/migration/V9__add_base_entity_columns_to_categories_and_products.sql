-- V9__add_base_entity_columns_to_categories_and_products.sql

-- Add deleted and version columns to categories table for BaseEntity compliance
ALTER TABLE categories ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_categories_deleted ON categories(deleted);

-- Add deleted and version columns to products table for BaseEntity compliance
ALTER TABLE products ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_products_deleted ON products(deleted);
