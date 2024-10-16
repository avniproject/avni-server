update address_level_type set name = name || ' (voided~' || id || ')',
                              last_modified_date_time = current_timestamp + (random() * 5000 * (interval '1 millisecond'))
                          where is_voided = true and name not ilike '%voided%';