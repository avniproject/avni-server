UPDATE translation
SET translation_json = replace(translation_json::text, 'OpenCHS', 'Avni')::jsonb,
    last_modified_date_time = current_timestamp
WHERE position(
    'Please give location permission to OpenCHS from Settings > Apps > OpenCHS' 
    in translation_json::text
) > 0;