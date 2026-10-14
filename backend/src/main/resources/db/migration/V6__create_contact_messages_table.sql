CREATE TABLE contact_messages (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    phone VARCHAR(50),
    country VARCHAR(100),
    department VARCHAR(100),
    subject VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    is_replied BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    replied_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX idx_contact_is_read ON contact_messages(is_read);
CREATE INDEX idx_contact_created_at ON contact_messages(created_at);
