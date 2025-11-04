CREATE TABLE template_organisation (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    summary TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    organisation_id INTEGER NOT NULL,
    created_by_id INTEGER,
    last_modified_by_id INTEGER,
    created_date_time TIMESTAMP WITH TIME ZONE,
    last_modified_date_time TIMESTAMP WITH TIME ZONE,
    is_voided BOOLEAN NOT NULL DEFAULT FALSE,
    version INTEGER NOT NULL DEFAULT 0,
    
    CONSTRAINT template_organisation_organisation_fk FOREIGN KEY (organisation_id) REFERENCES organisation (id),
    CONSTRAINT template_organisation_created_by_user_fk FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT template_organisation_last_modified_by_user_fk FOREIGN KEY (last_modified_by_id) REFERENCES users (id)
);

ALTER TABLE template_organisation ADD CONSTRAINT template_organisation_uuid_uniq UNIQUE (uuid);
ALTER TABLE template_organisation ADD CONSTRAINT template_organisation_name_uniq UNIQUE (name);
ALTER TABLE template_organisation ADD CONSTRAINT template_organisation_organisation_id_uniq UNIQUE (organisation_id);

GRANT ALL ON template_organisation TO public;
