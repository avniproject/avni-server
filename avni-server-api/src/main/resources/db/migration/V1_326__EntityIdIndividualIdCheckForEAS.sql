alter table entity_approval_status
    add check ( (entity_type <> 'Subject') or (entity_id = individual_id) );
