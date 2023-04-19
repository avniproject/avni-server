-- comment	{uuid}
delete
from comment
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid order by id desc) rnk
             from comment
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from comment
                       group by uuid
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE comment
    ADD UNIQUE (uuid);

-- comment_thread	{uuid}

delete
from comment_thread
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid order by id desc) rnk
             from comment_thread
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from comment_thread
                       group by uuid
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE comment_thread
    ADD UNIQUE (uuid);

-- individual_relationship	{uuid}

delete
from individual_relationship
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid order by id desc) rnk
             from individual_relationship
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from individual_relationship
                       group by uuid
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE individual_relationship
    ADD UNIQUE (uuid);

-- rule_failure_log	{uuid}

delete
from rule_failure_log
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid order by id desc) rnk
             from rule_failure_log
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from rule_failure_log
                       group by uuid
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE rule_failure_log
    ADD UNIQUE (uuid);

-- rule_failure_telemetry	{uuid}

delete
from rule_failure_telemetry
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid order by id desc) rnk
             from rule_failure_telemetry
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from rule_failure_telemetry
                       group by uuid
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE rule_failure_telemetry
    ADD UNIQUE (uuid);

-- subject_program_eligibility	{uuid}

delete
from subject_program_eligibility
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid order by id desc) rnk
             from subject_program_eligibility
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from subject_program_eligibility
                       group by uuid
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE subject_program_eligibility
    ADD UNIQUE (uuid);

-- task	{legacy_id,organisation_id}{uuid}

delete
from task
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid order by id desc) rnk
             from task
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from task
                       group by uuid
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE task
    ADD UNIQUE (uuid);
ALTER TABLE task
    ADD UNIQUE (legacy_id, organisation_id);

-- task_unassignment	{uuid}

delete
from task_unassignment
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid order by id desc) rnk
             from task_unassignment
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from task_unassignment
                       group by uuid
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE task_unassignment
    ADD UNIQUE (uuid);
