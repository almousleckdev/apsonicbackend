-- V13__fix_roles_permissions_and_user_roles.sql

-- Ensure DELETE_CATEGORIES and DELETE_NEWS permissions exist
INSERT INTO permissions (name, description) VALUES
('DELETE_CATEGORIES', 'Remove product categories'),
('DELETE_BLOGS', 'Remove blog news posts')
ON CONFLICT (name) DO NOTHING;

-- Grant all permissions to ROLE_SUPER_ADMIN
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Grant content management permissions to ROLE_ADMIN
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN (
    'READ_PRODUCTS', 'WRITE_PRODUCTS', 'DELETE_PRODUCTS',
    'READ_CATEGORIES', 'WRITE_CATEGORIES', 'DELETE_CATEGORIES',
    'READ_PAGES', 'WRITE_PAGES',
    'READ_BLOGS', 'WRITE_BLOGS', 'DELETE_BLOGS',
    'READ_MEDIA', 'WRITE_MEDIA',
    'READ_PARTNERS', 'WRITE_PARTNERS',
    'READ_TEAMS', 'WRITE_TEAMS'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Grant editor content permissions to ROLE_EDITOR (cannot delete products/categories)
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_EDITOR' AND p.name IN (
    'READ_PRODUCTS', 'WRITE_PRODUCTS',
    'READ_CATEGORIES', 'WRITE_CATEGORIES',
    'READ_PAGES', 'WRITE_PAGES',
    'READ_BLOGS', 'WRITE_BLOGS',
    'READ_MEDIA', 'WRITE_MEDIA'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Ensure default superadmin has ROLE_SUPER_ADMIN mapped
INSERT INTO users_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u CROSS JOIN roles r
WHERE u.username = 'superadmin' AND r.name = 'ROLE_SUPER_ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;
