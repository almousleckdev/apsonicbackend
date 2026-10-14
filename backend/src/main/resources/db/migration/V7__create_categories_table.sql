-- V7__create_categories_table.sql
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(100) NOT NULL UNIQUE,
    name_en VARCHAR(200) NOT NULL,
    name_fr VARCHAR(200),
    name_zh VARCHAR(200),
    image_url VARCHAR(1000),
    brand VARCHAR(100) NOT NULL DEFAULT 'apsonic',
    image_scale DOUBLE PRECISION DEFAULT 1.0,
    image_translate_y INT DEFAULT 0,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_categories_slug ON categories(slug);
CREATE INDEX idx_categories_brand ON categories(brand);
CREATE INDEX idx_categories_active ON categories(active);
CREATE INDEX idx_categories_display_order ON categories(display_order);

-- Seed initial default categories
INSERT INTO categories (slug, name_en, name_fr, name_zh, image_url, brand, display_order, active)
VALUES 
    ('street', 'Street', 'Urbaine', '街车', '/categories/街车/AP125-27 (2.2).png', 'apsonic', 1, true),
    ('offroad', 'Off-Road', 'Tout-Terrain', '越野车', '/products/AP250GY (1).png', 'apsonic', 2, true),
    ('underbone', 'Underbone', 'Underbone', '弯梁车', '/categories/弯梁车/AP110 (2).png', 'apsonic', 3, true),
    ('scooter', 'Scooter', 'Scooter', '踏板车', '/categories/踏板车/AP125T/AP125T 右侧 精修 白（2）.png', 'apsonic', 4, true),
    ('atv', 'ATV', 'Quad / ATV', 'ATV', '/categories/ATV/AP150ST-250 正右侧 投影.png', 'apsonic', 5, true),
    ('tricycle', 'Tricycle', 'Tricycle', '三轮车', '/categories/三轮车/AP150ZH-175 更新/AP150ZH-175 MAX (1).png', 'apsonic', 6, true),
    ('sport', 'Sport', 'Sport', '运动型', '/products/AP125-30（1）.png', 'apsonic-pro', 7, true),
    ('touring', 'Touring', 'Routière / Touring', '旅行型', '/products/AP125-12(1).png', 'apsonic-pro', 8, true);
