UPDATE organisation_config
    SET settings = jsonb_set(settings, '{useMinioForStorage}', 'true', false)
    WHERE settings ->> 'useMinioForStorage' = 'true';
UPDATE organisation_config
    SET settings = jsonb_set(settings, '{useMinioForStorage}', 'false', false)
    WHERE settings ->> 'useMinioForStorage' = 'false';
