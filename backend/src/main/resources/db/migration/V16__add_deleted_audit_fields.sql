-- Add soft delete fields to base tables
ALTER TABLE products ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE products ADD COLUMN deleted_by VARCHAR(100);

ALTER TABLE categories ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE categories ADD COLUMN deleted_by VARCHAR(100);

ALTER TABLE news ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE news ADD COLUMN deleted_by VARCHAR(100);

ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE users ADD COLUMN deleted_by VARCHAR(100);

-- Contact messages didn't have deleted column either, so add it
ALTER TABLE contact_messages ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE contact_messages ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE contact_messages ADD COLUMN deleted_by VARCHAR(100);
