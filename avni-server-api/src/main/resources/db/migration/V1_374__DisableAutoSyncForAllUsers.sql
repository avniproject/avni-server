update users
set settings = coalesce(settings, '{}'::jsonb) || '{"disableAutoSync": true}'::jsonb,
    last_modified_date_time = now();
