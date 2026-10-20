-- Menu items an administrator removed from the header or footer menu. They stay here (like every other deleted
-- record) until restored, deleted for good, or the 3-month trash cleanup runs.
CREATE TABLE cms_navigation_trash (
    id         BIGSERIAL PRIMARY KEY,
    menu_key   VARCHAR(20)  NOT NULL,
    title      VARCHAR(255) NOT NULL,
    item       JSONB        NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_by VARCHAR(100)
);
