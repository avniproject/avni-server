UPDATE report_card
SET standard_report_card_input='{
  "programs": [],
  "subjectTypes": [],
  "encounterTypes": [],
  "recentDuration": "{\"value\":\"1\",\"unit\":\"days\"}"
}'::jsonb,
    last_modified_date_time   = current_timestamp + random() * 5000 * (interval '1 millisecond')
FROM standard_report_card_type srct
WHERE standard_report_card_input = '{}'::jsonb
  AND standard_report_card_type_id IS NOT NULL
  AND standard_report_card_type_id = srct.id
  AND srct.uuid in ('88a7514c-48c0-4d5d-a421-d074e43bb36c',
                    'a5efc04c-317a-4823-a203-e62603454a65',
                    '77b5b3fa-de35-4f24-996b-2842492ea6e0');
