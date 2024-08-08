CREATE OR REPLACE FUNCTION grant_all_on_table(rolename text, tablename text)
    RETURNS text AS
$body$
BEGIN
    EXECUTE (
        SELECT 'GRANT ALL ON TABLE '
                   || tablename
                   || ' TO ' || quote_ident(rolename)
    );

    EXECUTE (
        SELECT 'GRANT SELECT ON '
                   || tablename
                   || ' TO ' || quote_ident(rolename)
    );

    EXECUTE 'GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO ' || quote_ident(rolename) || '';
    RETURN 'ALL PERMISSIONS GRANTED TO ' || quote_ident(rolename);
END;
$body$ LANGUAGE plpgsql;

SELECT grant_all_on_table(a.rolname, 'organisation_category') FROM pg_roles a WHERE pg_has_role('openchs', a.oid, 'member');
SELECT grant_all_on_table(a.rolname, 'organisation_status') FROM pg_roles a WHERE pg_has_role('openchs', a.oid, 'member');
