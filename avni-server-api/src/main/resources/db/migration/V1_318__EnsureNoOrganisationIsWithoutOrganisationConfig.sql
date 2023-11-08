insert into organisation_config (uuid, organisation_id, settings, created_by_id, last_modified_by_id, created_date_time,
                                 last_modified_date_time)
    (
        select uuid_generate_v4(),
               o.id,
               '{
                 "languages": [
                   "en"
                 ]
               }'::jsonb, 5661,
               5661,
               now(),
               now()
        from public.organisation o
                 left join organisation_config oc on o.id = oc.organisation_id
        where oc.id is null
    );
