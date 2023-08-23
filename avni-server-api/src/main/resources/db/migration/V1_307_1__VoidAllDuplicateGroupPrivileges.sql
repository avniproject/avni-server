update group_privilege
set is_voided = true and last_modified_date_time = current_timestamp
WHERE id IN
      (
          SELECT ctid
          FROM (
                   SELECT gp.id                                                                                                                            as ctid,
                          gp.group_id,
                          gp.privilege_id,
                          gp.subject_type_id,
                          gp.program_id,
                          gp.program_encounter_type_id,
                          row_number()
                          OVER (PARTITION BY gp.group_id, gp.privilege_id, gp.subject_type_id, gp.program_id, gp.program_encounter_type_id ORDER BY gp.id) AS rnum
                   FROM group_privilege gp
                   where gp.is_voided is false
                   order by 2, 3, 4, 5, 6) t
          WHERE t.rnum > 1);