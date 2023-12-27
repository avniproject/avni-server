UPDATE public.privilege
SET description = 'Create and update users and their subject, task , group or identifier assignments', last_modified_date_time = now()
WHERE name = 'Edit user configuration' and type = 'EditUserConfiguration';

UPDATE public.privilege
SET description = 'Edit organisation configuration like filters, languages, userMessagingConfig, etc.,', last_modified_date_time = now()
WHERE name = 'Edit organisation configuration' and type = 'EditOrganisationConfiguration';