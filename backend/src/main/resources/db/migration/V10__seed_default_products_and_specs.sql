-- V10__seed_default_products_and_specs.sql

-- Seed AP110-A PLUS Product
INSERT INTO products (
    id, slug, model_name, category_id, brand, displacement, 
    cover_image_url, hero_image_url, performance_image_url, 
    featured, active, display_order, views_count, deleted, version,
    created_at, updated_at
) VALUES (
    1,
    'ap110-a-plus',
    'AP110-A PLUS',
    (SELECT id FROM categories WHERE slug = 'underbone' LIMIT 1),
    'apsonic',
    110,
    '/categories/弯梁车/AP110-A PLUS(白).png',
    '/products/details/producdetailhero.jpg',
    '/products/details/performance.jpg',
    true,
    true,
    1,
    0,
    false,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (slug) DO UPDATE SET
    hero_image_url = EXCLUDED.hero_image_url,
    performance_image_url = EXCLUDED.performance_image_url,
    cover_image_url = EXCLUDED.cover_image_url;

-- Section 3, 4, 5 full-bleed picture banners for AP110-A PLUS
DELETE FROM product_spec_banners WHERE product_id = 1;
INSERT INTO product_spec_banners (product_id, image_url, title, display_order) VALUES
(1, '/products/details/thirdsection.jpg', 'Reinforced Chassis & Heavy Duty Frame', 1),
(1, '/products/details/fourthsection.jpg', 'Extended Heavy Cargo Rear Carrier', 2),
(1, '/products/details/fivessection.jpg', 'High-Efficiency Eco Combustion Engine', 3);

-- Section 6: Color Variants for AP110-A PLUS
DELETE FROM product_colors WHERE product_id = 1;
INSERT INTO product_colors (product_id, color_name, hex_code, image_url, display_order) VALUES
(1, 'White', '#FFFFFF', '/categories/弯梁车/AP110-A PLUS(白).png', 1),
(1, 'Red', '#E53E3E', '/categories/弯梁车/AP110-A PLUS(红).png', 2),
(1, 'Blue', '#3182CE', '/categories/弯梁车/AP110-A PLUS(蓝).png', 3);

-- Section 7: Technical Specifications (Key metrics & Full specs)
DELETE FROM product_specs WHERE product_id = 1;
INSERT INTO product_specs (product_id, spec_key, spec_value, spec_group, unit, is_key_metric, display_order) VALUES
(1, 'MAX SPEED', '95', 'KEY_METRIC', 'KM/H', true, 1),
(1, 'FUEL TANK CAPACITY', '4.5', 'KEY_METRIC', 'L', true, 2),
(1, 'N.W', '98', 'KEY_METRIC', 'KG', true, 3),
(1, 'GROUND CLEARANCE', '150', 'KEY_METRIC', 'MM', true, 4),
(1, 'DISPLACEMENT(ML)', '109.7', 'ENGINE', 'ML', false, 5),
(1, 'Wheelbase(MM)', '1225', 'DIMENSIONS', 'MM', false, 6),
(1, 'BORE(MM)XSTROKE(MM)', '50.0 x 55.6', 'ENGINE', 'MM', false, 7),
(1, 'L(MM)XW(MM)XH(MM)', '1920 x 700 x 1080', 'DIMENSIONS', 'MM', false, 8),
(1, 'COMPRESSION RATIO', '9.0:1', 'ENGINE', '', false, 9),
(1, 'MIN GROUND CLEARANCE(MM)', '150', 'DIMENSIONS', 'MM', false, 10),
(1, 'MAX POWER(KW/RPM)', '5.5 / 7500', 'ENGINE', 'KW/RPM', false, 11),
(1, 'N.W(KG)', '98', 'CHASSIS', 'KG', false, 12),
(1, 'MAX TORQUE(N.m/RPM)', '7.8 / 5500', 'ENGINE', 'N.m/RPM', false, 13),
(1, 'BRAKE', 'Disc / Drum', 'CHASSIS', '', false, 14),
(1, 'MAX SPEED(KM/H)', '95', 'ENGINE', 'KM/H', false, 15),
(1, 'TYRE SIZE', '2.50-17 / 2.75-17', 'CHASSIS', '', false, 16),
(1, 'IGNITION', 'CDI', 'ELECTRICAL', '', false, 17),
(1, 'FUEL TANK CAPACITY(L)', '4.5', 'ENGINE', 'L', false, 18),
(1, 'STARTER', 'Electric / Kick', 'ELECTRICAL', '', false, 19),
(1, 'CLUTCH TYPE', 'Semi-Automatic / 4-Speed', 'ENGINE', '', false, 20);

-- Sync primary key sequence for products
SELECT setval('products_id_seq', (SELECT COALESCE(MAX(id), 1) FROM products));
