UPDATE report_card
SET standard_report_card_input='{
  "programs": [],
  "subjectTypes": [],
  "encounterTypes": []
}'::jsonb
FROM standard_report_card_type srct
WHERE standard_report_card_input = '{}'::jsonb
  AND standard_report_card_type_id IS NOT NULL
  AND standard_report_card_type_id = srct.id
  AND srct.uuid in ('27020b32-c21b-43a4-81bd-7b88ad3a6ef0',
                    '9f88bee5-2ab9-4ac4-ae19-d07e9715bdb5',
                    '1fbcadf3-bf1a-439e-9e13-24adddfbf6c0');
