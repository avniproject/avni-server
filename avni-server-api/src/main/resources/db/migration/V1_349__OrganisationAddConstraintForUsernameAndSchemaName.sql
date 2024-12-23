ALTER TABLE organisation ADD CONSTRAINT organisation_username_suffix_key UNIQUE (username_suffix);
ALTER TABLE organisation ADD CONSTRAINT organisation_schema_name_key UNIQUE (schema_name);