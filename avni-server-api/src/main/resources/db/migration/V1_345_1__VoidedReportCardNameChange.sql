update report_card
set name = concat(name, ' (voided~', id, ')')
where is_voided
  and name not ilike '%voided%';