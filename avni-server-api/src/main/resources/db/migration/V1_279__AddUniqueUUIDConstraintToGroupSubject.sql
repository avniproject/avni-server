delete
from group_subject
where id in
      (select id
       from (select id,
                    rank() over (partition by group_subject_id, member_subject_id, is_voided order by id desc) rnk
             from group_subject
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from group_subject
                       group by organisation_id, uuid, group_subject_id, member_subject_id, is_voided
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE group_subject
  ADD UNIQUE (uuid, organisation_id);
