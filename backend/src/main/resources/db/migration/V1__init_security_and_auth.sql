-- 1. Permissions Table
CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Roles Table
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Roles <-> Permissions Junction Table
CREATE TABLE roles_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- 4. Users Table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(150) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_locked BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- 5. Users and Roles
CREATE TABLE users_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- 6. Refresh Tokens Table
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(512) NOT NULL UNIQUE,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for optimal lookup performance
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- Permissions
INSERT INTO permissions (name, description) VALUES
('READ_PRODUCTS', 'View product catalog'),
('WRITE_PRODUCTS', 'Create and edit products'),
('DELETE_PRODUCTS', 'Remove products'),
('READ_CATEGORIES', 'View categories'),
('WRITE_CATEGORIES', 'Create and edit categories'),
('READ_PAGES', 'View site pages'),
('WRITE_PAGES', 'Create and edit site pages'),
('READ_BLOGS', 'View blog posts'),
('WRITE_BLOGS', 'Create and edit blog posts'),
('READ_MEDIA', 'View media assets'),
('WRITE_MEDIA', 'Upload and manage media'),
('READ_PARTNERS', 'View partners content'),
('WRITE_PARTNERS', 'Manage partners content'),
('READ_TEAMS', 'View team members'),
('WRITE_TEAMS', 'Manage team members'),
('MANAGE_USERS', 'Full access to manage platform users'),
('MANAGE_ROLES', 'Full access to manage roles and permissions');

-- Roles
INSERT INTO roles (name, description) VALUES
('ROLE_SUPER_ADMIN', 'Super administrator with full system permissions'),
('ROLE_ADMIN', 'Administrator with CMS content and management permissions'),
('ROLE_EDITOR', 'Editor with content management permissions'),
('ROLE_USER', 'Standard registered user');

-- Map all permissions to ROLE_SUPER_ADMIN to have control
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ROLE_SUPER_ADMIN';

-- Map content permissions to ROLE_ADMIN
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN (
    'READ_PRODUCTS', 'WRITE_PRODUCTS', 'DELETE_PRODUCTS',
    'READ_CATEGORIES', 'WRITE_CATEGORIES',
    'READ_PAGES', 'WRITE_PAGES',
    'READ_BLOGS', 'WRITE_BLOGS',
    'READ_MEDIA', 'WRITE_MEDIA',
    'READ_PARTNERS', 'WRITE_PARTNERS',
    'READ_TEAMS', 'WRITE_TEAMS'
);

-- Map editor permissions to ROLE_EDITOR
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_EDITOR' AND p.name IN (
    'READ_PRODUCTS', 'WRITE_PRODUCTS',
    'READ_CATEGORIES', 'WRITE_CATEGORIES',
    'READ_PAGES', 'WRITE_PAGES',
    'READ_BLOGS', 'WRITE_BLOGS',
    'READ_MEDIA', 'WRITE_MEDIA'
);

-- Insert Default Super Admin User (Password: Admin@123)
INSERT INTO users (email, username, password, first_name, last_name, enabled, account_non_expired, credentials_non_expired, account_non_locked, created_by, updated_by)
VALUES (
    'admin@apsonic.com',
    'superadmin',
    '$2a$10$jpoqFMV6HETV8JJC2ghcAehy8n/q.2j9kwDgR0gnl2RGW2mKIG7oa',
    'System',
    'Administrator',
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    'SYSTEM',
    'SYSTEM'
);

-- Assign ROLE_SUPER_ADMIN to default super admin user
INSERT INTO users_roles (user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE u.username = 'superadmin' AND r.name = 'ROLE_SUPER_ADMIN';