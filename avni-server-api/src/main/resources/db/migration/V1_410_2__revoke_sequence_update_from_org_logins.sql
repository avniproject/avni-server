-- Organisation logins (and PUBLIC) lose UPDATE on the shared sequences in public. With UPDATE a login can
-- reset a counter that numbers new records for every organisation (setval), which breaks saves for all of
-- them. USAGE and SELECT stay, so nextval and currval keep working. R__Functions' grant helpers stop
-- granting UPDATE to new logins.
--
-- Idempotent: production had this applied by hand on 12 Nov, and then it finds nothing to revoke.
-- One REVOKE per sequence naming every grantee. Only sequences owned by a role the migrating login can act
-- as are touched; anything left is named in the deploy log.
--
-- The three grant helpers are redefined here, not only in R__Functions: Flyway runs every pending versioned
-- migration before any repeatable one, so a later migration in the same deploy (V1_411 onwards on the abdm
-- line, or any new table) would otherwise call the old helpers and grant ALL again. R__Functions then
-- replaces them with the same bodies.

CREATE OR REPLACE FUNCTION revoke_write_on_account_tables(rolename text)
    RETURNS text AS
$body$
BEGIN
    -- Callers pass every role openchs is a member of, openchs included, and on RDS openchs is not a superuser:
    -- never narrow the tables' owner (or anything that acts as it), or Avni loses write on its own account tables.
    IF pg_has_role(rolename, (SELECT relowner FROM pg_class WHERE oid = 'public.account_admin'::regclass), 'USAGE')
        OR pg_has_role(rolename, (SELECT relowner FROM pg_class WHERE oid = 'public.account'::regclass), 'USAGE')
        OR (SELECT rolsuper FROM pg_roles WHERE rolname = rolename) THEN
        RETURN 'skipped owner ' || quote_ident(rolename);
    END IF;
    EXECUTE format('REVOKE ALL ON TABLE public.account_admin, public.account FROM %I', rolename);
    EXECUTE format('GRANT SELECT ON TABLE public.account_admin, public.account TO %I', rolename);
    RETURN 'account and account_admin read-only for ' || quote_ident(rolename);
END;
$body$ LANGUAGE plpgsql;

-- Organisation logins get read and write, never TRIGGER or TRUNCATE: a trigger they attach runs as whoever
-- writes to the table (openchs), and TRUNCATE ignores row level security. The two account tables are
-- read-only for them, since a row in account_admin makes a user an Avni super admin.
CREATE OR REPLACE FUNCTION grant_all_on_table(rolename text, tablename text)
    RETURNS text AS
$body$
BEGIN
    EXECUTE (SELECT 'GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON TABLE '
                        || tablename
                        || ' TO ' || quote_ident(rolename));

    PERFORM revoke_write_on_account_tables(rolename);

    EXECUTE (SELECT 'GRANT SELECT ON '
                        || tablename
                        || ' TO ' || quote_ident(rolename));

    -- USAGE and SELECT number new records; UPDATE would let the login reset a counter every organisation shares.
    EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ' || quote_ident(rolename) || '';
    RETURN 'ALL PERMISSIONS GRANTED TO ' || quote_ident(rolename);
END;
$body$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION grant_all_on_all(rolename text)
    RETURNS text AS
$body$
BEGIN
    EXECUTE (SELECT 'GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON TABLE '
                        || string_agg(format('%I.%I', table_schema, table_name), ',')
                        || ' TO ' || quote_ident(rolename) || ''
             FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_type = 'BASE TABLE');

    PERFORM revoke_write_on_account_tables(rolename);

    EXECUTE (SELECT 'GRANT SELECT ON '
                        || string_agg(format('%I.%I', schemaname, viewname), ',')
                        || ' TO ' || quote_ident(rolename) || ''
             FROM pg_catalog.pg_views
             WHERE schemaname = 'public'
               and viewowner in ('openchs'));

    -- USAGE and SELECT number new records; UPDATE would let the login reset a counter every organisation shares.
    EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ' || quote_ident(rolename) || '';
    EXECUTE 'GRANT ALL ON ALL FUNCTIONS IN SCHEMA public TO ' || quote_ident(rolename) || '';
    RETURN 'ALL PERMISSIONS GRANTED TO ' || quote_ident(rolename);
END;
$body$ LANGUAGE plpgsql;

DO
$$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT c.oid::regclass AS seq,
               string_agg(DISTINCT CASE WHEN a.grantee = 0 THEN 'PUBLIC' ELSE quote_ident(pg_get_userbyid(a.grantee)) END,
                          ', ') AS grantees
        FROM pg_class c,
             aclexplode(c.relacl) a
        WHERE c.relkind = 'S'
          AND c.relnamespace = 'public'::regnamespace
          AND pg_has_role(current_user, c.relowner, 'USAGE')
          AND a.privilege_type = 'UPDATE'
          AND a.grantee <> c.relowner
        GROUP BY c.oid
    LOOP
        EXECUTE format('REVOKE UPDATE ON SEQUENCE %s FROM %s CASCADE', r.seq, r.grantees);
    END LOOP;

    FOR r IN
        SELECT DISTINCT c.oid::regclass AS seq
        FROM pg_class c,
             aclexplode(c.relacl) a
        WHERE c.relkind = 'S'
          AND c.relnamespace = 'public'::regnamespace
          AND a.privilege_type = 'UPDATE'
          AND a.grantee <> c.relowner
    LOOP
        RAISE WARNING 'V1_410_2: % still grants UPDATE to a login other than its owner', r.seq;
    END LOOP;
END
$$;
