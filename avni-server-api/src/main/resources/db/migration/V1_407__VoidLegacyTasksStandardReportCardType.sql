-- 'Tasks' was added in V1_238. V1_244 then added 'Call tasks' and 'Open subject tasks' but did not
-- void the V1_238 row, so it stayed live and kept showing up in the app designer. It is not supported
-- in the client either, so voiding it is safe.
update standard_report_card_type
set is_voided               = true,
    last_modified_date_time = current_timestamp
where type = 'Tasks';
