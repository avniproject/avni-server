INSERT INTO standard_report_card_type ( uuid, name, description, is_voided, created_date_time,
                                              last_modified_date_time)
VALUES ( uuid_generate_v4(), 'Due checklist', 'Due checklist', false,
        now(), now());

