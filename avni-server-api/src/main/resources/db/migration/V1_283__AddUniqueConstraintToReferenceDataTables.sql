-- entity_approval_status	{approval_status_id,organisation_id} AND {uuid,organisation_id}

delete
from entity_approval_status
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from entity_approval_status
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from entity_approval_status
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE entity_approval_status
    ADD UNIQUE (uuid, organisation_id);

-- external_system_config	{uuid,organisation_id}

delete
from external_system_config
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from external_system_config
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from external_system_config
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE external_system_config
    ADD UNIQUE (uuid, organisation_id);

-- group_dashboard	{uuid,organisation_id}

delete
from group_dashboard
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from group_dashboard
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from group_dashboard
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE group_dashboard
    ADD UNIQUE (uuid, organisation_id);

-- group_privilege	{uuid,organisation_id}

delete
from group_privilege
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from group_privilege
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from group_privilege
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE group_privilege
    ADD UNIQUE (uuid, organisation_id);

-- groups	{uuid,organisation_id}

delete
from groups
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from groups
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from groups
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE groups
    ADD UNIQUE (uuid, organisation_id);

-- individual_relation_gender_mapping	{uuid,organisation_id}

delete
from individual_relation_gender_mapping
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from individual_relation_gender_mapping
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from individual_relation_gender_mapping
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE individual_relation_gender_mapping
    ADD UNIQUE (uuid, organisation_id);


-- individual_relationship_type	{uuid,organisation_id}

delete
from individual_relationship_type
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from individual_relationship_type
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from individual_relationship_type
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE individual_relationship_type
    ADD UNIQUE (uuid, organisation_id);

-- individual_relation	{uuid,organisation_id}

delete
from individual_relation_gender_mapping
where relation_id in
      (select id
       from (select id, uuid, organisation_id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from individual_relation
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from individual_relation
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

delete
from individual_relationship
where relationship_type_id in
      (select id from individual_relationship_type irt
      where individual_a_is_to_b_relation_id in
          (select id
           from (select id, uuid, organisation_id,
                        rank() over (partition by uuid,organisation_id order by id desc) rnk
                 from individual_relation
                 where uuid in (
                     -- Duplicate uuids query
                     select uuid
                     from (select uuid, count(*)
                           from individual_relation
                           group by uuid, organisation_id
                           having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
           where rnk > 1));

delete
from individual_relationship
where relationship_type_id in
      (select id from individual_relationship_type irt
      where individual_b_is_to_a_relation_id in
         (select id
          from (select id, uuid, organisation_id,
                       rank() over (partition by uuid,organisation_id order by id desc) rnk
                from individual_relation
                where uuid in (
                    -- Duplicate uuids query
                    select uuid
                    from (select uuid, count(*)
                          from individual_relation
                          group by uuid, organisation_id
                          having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
          where rnk > 1));

delete
from individual_relationship_type
where individual_a_is_to_b_relation_id in
      (select id
       from (select id, uuid, organisation_id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from individual_relation
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from individual_relation
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

delete
from individual_relationship_type
where individual_b_is_to_a_relation_id in
      (select id
       from (select id, uuid, organisation_id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from individual_relation
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from individual_relation
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

delete
from individual_relation
where id in
      (select id
       from (select id, uuid, organisation_id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from individual_relation
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from individual_relation
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE individual_relation
    ADD UNIQUE (uuid, organisation_id);

-- location_location_mapping	{uuid,organisation_id}

delete
from location_location_mapping
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from location_location_mapping
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from location_location_mapping
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE location_location_mapping
    ADD UNIQUE (uuid, organisation_id);

-- menu_item	{uuid,organisation_id}

delete
from menu_item
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from menu_item
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from menu_item
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE menu_item
    ADD UNIQUE (uuid, organisation_id);

-- msg91_config	{uuid,organisation_id}

delete
from msg91_config
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from msg91_config
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from msg91_config
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE msg91_config
    ADD UNIQUE (uuid, organisation_id);

-- news	{uuid}

delete
from news
where id in
      (select id
       from (select id,
                    rank() over (order by id desc) rnk
             from news
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from news
                       group by uuid
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE news
    ADD UNIQUE (uuid);

-- subject_type	{uuid,organisation_id}

delete
from subject_type
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from subject_type
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from subject_type
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE subject_type
    ADD UNIQUE (uuid, organisation_id);

-- task_status	{uuid,organisation_id}

delete
from task_status
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from task_status
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from task_status
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE task_status
    ADD UNIQUE (uuid, organisation_id);

-- task_type	{uuid,organisation_id}

delete
from task_type
where id in
      (select id
       from (select id,
                    rank() over (partition by uuid,organisation_id order by id desc) rnk
             from task_type
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from task_type
                       group by uuid, organisation_id
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE task_type
    ADD UNIQUE (uuid, organisation_id);

-- user_group	{user_id, group_id}{uuid}

delete
from user_group
where id in
      (select id
       from (select id,
                    rank() over (order by id desc) rnk
             from user_group
             where uuid in (
                 -- Duplicate uuids query
                 select uuid
                 from (select uuid, count(*)
                       from user_group
                       group by uuid
                       having count(*) > 1) duplicate_uuids)) id_of_duplicate_uuids
       where rnk > 1);

ALTER TABLE user_group
    ADD UNIQUE (uuid);
