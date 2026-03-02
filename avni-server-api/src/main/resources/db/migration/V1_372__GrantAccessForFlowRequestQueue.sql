SELECT grant_all_on_table(a.rolname, 'flow_request_queue')
FROM pg_roles a
WHERE pg_has_role('openchs', a.oid, 'member');
