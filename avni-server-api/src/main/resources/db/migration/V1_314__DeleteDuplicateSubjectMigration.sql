with duplicate_subject_migrations as (select sm1.id   as duplicate_row_id,
                                             sm1.uuid as duplicate_row_uuid,
                                             sm2.id   as non_duplicate_row_id,
                                             sm2.uuid as non_duplicate_row_uuid
                                      from subject_migration sm1
                                             inner join subject_migration sm2
                                                        on sm1.id - sm2.id = 1
                                                          and sm1.individual_id = sm2.individual_id
                                                          and sm1.organisation_id = sm2.organisation_id
                                                          and sm1.subject_type_id = sm2.subject_type_id
                                                          and
                                                           sm1.old_address_level_id is not distinct from sm2.old_address_level_id
                                                          and
                                                           sm1.new_address_level_id is not distinct from sm2.new_address_level_id
                                                          and
                                                           sm1.old_sync_concept_1_value is not distinct from sm2.old_sync_concept_1_value
                                                          and
                                                           sm1.new_sync_concept_1_value is not distinct from sm2.new_sync_concept_1_value
--          and sm1.old_sync_concept_2_value is not distinct from sm2.old_sync_concept_2_value
--          and sm1.new_sync_concept_2_value is not distinct from sm2.new_sync_concept_2_value
)
    ,
     filtered_duplicate_subject_migrations as (select *
                                               from duplicate_subject_migrations
                                               where non_duplicate_row_id not in
                                                     (select duplicate_row_id from duplicate_subject_migrations)),
     non_dupe_history_updates as (
       update subject_migration set manual_update_history = case when manual_update_history is null
         then concat(to_char(current_timestamp, 'DD/MM/YYYY hh:mm:ss'), ' - ', 'duplicate row with id ' ||
                                                                               fdsm.duplicate_row_id ||
                                                                               ' and uuid ' ||
                                                                               fdsm.duplicate_row_uuid ||
                                                                               ' deleted for avniproject/avni-server#618')
         else
           concat(to_char(current_timestamp, 'DD/MM/YYYY hh:mm:ss'), ' - ', 'duplicate row with id ' ||
                                                                            fdsm.duplicate_row_id ||
                                                                            ' and uuid ' ||
                                                                            fdsm.duplicate_row_uuid ||
                                                                            ' deleted for avniproject/avni-server#618' ||
                                                                            '; ' ||
                                                                            manual_update_history)
         end
         from filtered_duplicate_subject_migrations fdsm
         where id = fdsm.non_duplicate_row_id)
delete
from subject_migration sm
where id in (select duplicate_row_id
             from filtered_duplicate_subject_migrations);