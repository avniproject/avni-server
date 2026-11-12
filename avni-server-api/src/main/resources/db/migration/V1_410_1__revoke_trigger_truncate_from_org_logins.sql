-- V1_410_1 (numbered to sit beside V1_410; the abdm line already uses V1_411). Organisation logins (and PUBLIC, on the few tables an old migration granted "to public") lose TRIGGER and
-- TRUNCATE on every public table and view, and keep only SELECT on account and account_admin.
-- A trigger an organisation login attaches runs as whoever writes to the table (openchs), so it reads and
-- changes every organisation's data; TRUNCATE ignores row level security; a row in account_admin makes a
-- user an Avni super admin. R__Functions' grant helpers stop granting these to new logins.
--
-- Idempotent: production may already have had this applied by hand, and then it finds nothing to revoke.
-- One REVOKE per table naming every grantee, so each table's privilege list is rewritten once, not once per
-- organisation. Only tables owned by the migrating login are touched.

DO
$$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT c.oid::regclass AS tbl,
               string_agg(DISTINCT CASE WHEN a.grantee = 0 THEN 'PUBLIC' ELSE quote_ident(pg_get_userbyid(a.grantee)) END,
                          ', ') AS grantees
        FROM pg_class c,
             aclexplode(c.relacl) a
        WHERE c.relnamespace = 'public'::regnamespace
          AND pg_has_role(current_user, c.relowner, 'USAGE')
          AND a.privilege_type IN ('TRIGGER', 'TRUNCATE')
          AND a.grantee <> c.relowner
        GROUP BY c.oid
    LOOP
        EXECUTE format('REVOKE TRIGGER, TRUNCATE ON TABLE %s FROM %s', r.tbl, r.grantees);
    END LOOP;

    FOR r IN
        SELECT c.oid::regclass AS tbl,
               string_agg(DISTINCT quote_ident(pg_get_userbyid(a.grantee)), ', ') AS grantees
        FROM pg_class c,
             aclexplode(c.relacl) a
        WHERE c.oid IN ('public.account_admin'::regclass, 'public.account'::regclass)
          AND a.grantee NOT IN (0, c.relowner)
          AND a.grantee <> (SELECT oid FROM pg_roles WHERE rolname = current_user)
          AND a.privilege_type <> 'SELECT'
        GROUP BY c.oid
    LOOP
        EXECUTE format('REVOKE ALL ON TABLE %s FROM %s', r.tbl, r.grantees);
        EXECUTE format('GRANT SELECT ON TABLE %s TO %s', r.tbl, r.grantees);
    END LOOP;

    -- PUBLIC counts for every login; it keeps no write on the account tables either (SELECT left as it is).
    REVOKE INSERT, UPDATE, DELETE, TRUNCATE, TRIGGER ON TABLE public.account_admin, public.account FROM PUBLIC;

    -- Anything left (a table owned by a role this login cannot act as, or a grant made by someone other than
    -- the owner) is named in the deploy log rather than skipped silently.
    FOR r IN
        SELECT DISTINCT c.oid::regclass AS tbl
        FROM pg_class c,
             aclexplode(c.relacl) a
        WHERE c.relnamespace = 'public'::regnamespace
          AND a.privilege_type IN ('TRIGGER', 'TRUNCATE')
          AND a.grantee <> c.relowner
    LOOP
        RAISE WARNING 'V1_410_1: % still grants TRIGGER or TRUNCATE to a login other than its owner', r.tbl;
    END LOOP;
END
$$;
