SELECT grant_all_on_table(a.rolname, 'post_etl_sync_status')
FROM pg_roles a
WHERE pg_has_role('openchs', a.oid, 'member');
