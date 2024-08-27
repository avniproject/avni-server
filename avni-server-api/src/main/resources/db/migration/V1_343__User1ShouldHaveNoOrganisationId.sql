-- In earlier migration we have set parent_organisation as 1 but fix in production
update users set organisation_id = null where id = 1 and username = 'admin';
