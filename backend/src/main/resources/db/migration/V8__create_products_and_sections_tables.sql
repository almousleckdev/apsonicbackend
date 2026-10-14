-- V8__create_products_and_sections_tables.sql

-- 1. Main Products Table
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(200) NOT NULL UNIQUE,
    model_name VARCHAR(200) NOT NULL,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    brand VARCHAR(100) NOT NULL DEFAULT 'apsonic',
    displacement INT,
    cover_image_url VARCHAR(1000) NOT NULL,
    hero_image_url VARCHAR(1000),
    performance_image_url VARCHAR(1000),
    story_title VARCHAR(300),
    story_description TEXT,
    story_background_image VARCHAR(1000),
    aesthetic_title VARCHAR(300),
    aesthetic_subtitle VARCHAR(300),
    aesthetic_bg_image VARCHAR(1000),
    aesthetic_image_1 VARCHAR(1000),
    aesthetic_image_2 VARCHAR(1000),
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    views_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_slug ON products(slug);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_brand ON products(brand);
CREATE INDEX idx_products_displacement ON products(displacement);
CREATE INDEX idx_products_active ON products(active);
CREATE INDEX idx_products_featured ON products(featured);

-- 2. Product Color Variants Table
CREATE TABLE product_colors (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    color_name VARCHAR(100) NOT NULL,
    hex_code VARCHAR(50) NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    display_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_product_colors_product ON product_colors(product_id);

-- 3. Product Specification Banners (Section 3, 4, 5...) Table
CREATE TABLE product_spec_banners (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    image_url VARCHAR(1000) NOT NULL,
    title VARCHAR(300),
    description TEXT,
    metric_label VARCHAR(100),
    metric_value VARCHAR(100),
    metric_unit VARCHAR(50),
    display_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_product_spec_banners_product ON product_spec_banners(product_id);

-- 4. Product Technical Specifications Table
CREATE TABLE product_specs (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    spec_key VARCHAR(100) NOT NULL,
    spec_value VARCHAR(255) NOT NULL,
    spec_group VARCHAR(100) NOT NULL DEFAULT 'ENGINE', -- KEY_METRIC, ENGINE, DIMENSIONS, CHASSIS, ELECTRICAL
    unit VARCHAR(50),
    is_key_metric BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_product_specs_product ON product_specs(product_id);

-- 5. Product Customer Reviews Table
CREATE TABLE product_reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    author_name VARCHAR(150) NOT NULL,
    rating INT NOT NULL DEFAULT 5,
    comment TEXT NOT NULL,
    review_date VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_reviews_product ON product_reviews(product_id);
