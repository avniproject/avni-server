package org.avni.server.rls;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Applied-state assertions for the org-scoped RLS policies (`*_orgs`).
 *
 * History: #1008 (V1_398) shipped `organisation_id = ANY(<stable fn>())` - re-evaluated per index tuple,
 * collapsed prod; #1029 (V1_405) reverted to the pre-17.0 OR-based USING (once per query, but seq scans);
 * #1030 (V1_406) re-lands the index plan with single evaluation via
 * `organisation_id = ANY (COALESCE((SELECT <fn>()), '{}'::integer[]))` - the scalar sublink is a once-per-query
 * InitPlan (the COALESCE is the fail-closed NULL guard) and the qual is an indexable ScalarArrayOpExpr. The two
 * shape assertions retired by the revert are recovered here for that InitPlan shape;
 * RlsPolicyPlanShapeIntegrationTest proves the plan behaviour through a real policy under SET ROLE.
 */
public class IndexableRlsPolicyMigrationIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private DataSource dataSource;
    private JdbcTemplate jdbc;

    private JdbcTemplate jdbc() {
        if (jdbc == null) jdbc = new JdbcTemplate(dataSource);
        return jdbc;
    }

    private int count(String sql) {
        return jdbc().queryForObject(sql, Integer.class);
    }

    @Test
    public void bothHelperFunctionsAreStableSecurityInvokerReturningIntArray() {
        List<Map<String, Object>> functions = jdbc().queryForList(
                "select proname, provolatile, prosecdef, pg_get_function_result(oid) ret " +
                        "from pg_proc where pronamespace = 'public'::regnamespace " +
                        "and proname in ('rls_visible_org_ids', 'rls_visible_org_ids_with_ancestors') order by proname");

        assertEquals("both helper functions must exist", 2, functions.size());
        for (Map<String, Object> function : functions) {
            String name = (String) function.get("proname");
            assertEquals(name + " must be STABLE (plan-time evaluation enables the index bound)",
                    "s", String.valueOf(function.get("provolatile")));
            assertEquals(name + " must be SECURITY INVOKER (subqueries run with the caller's RLS)",
                    Boolean.FALSE, function.get("prosecdef"));
            assertEquals(name + " must return integer[]", "integer[]", String.valueOf(function.get("ret")));
        }
    }

    @Test
    public void everyOrgScopedPolicyNameMatchesItsTable() {
        assertEquals("renamed-table policies must have been canonicalized to <table>_orgs", 0,
                count("select count(*) from pg_policy p " +
                        "join pg_class c on c.oid = p.polrelid " +
                        "join pg_namespace n on n.oid = c.relnamespace " +
                        "where n.nspname = 'public' and p.polname like '%\\_orgs' " +
                        "and p.polname <> c.relname || '_orgs'"));
    }

    @Test
    public void writeRestrictionRemainsOwnOrgOnly() {
        // WITH CHECK is unchanged by this migration: it must stay identical across all *_orgs policies and scope
        // writes to the caller's own org (the db_user -> own-org-id subselect), NOT widened to groups/ancestors.
        List<String> distinctChecks = jdbc().queryForList(
                "select distinct with_check from pg_policies where policyname like '%\\_orgs'", String.class);
        assertEquals("all *_orgs WITH CHECK clauses must be identical", 1, distinctChecks.size());

        String withCheck = distinctChecks.get(0);
        assertNotNull("WITH CHECK must not be null", withCheck);
        assertTrue("WITH CHECK must scope writes to the own org via db_user = CURRENT_USER, was: " + withCheck,
                withCheck.contains("db_user") && withCheck.toUpperCase().contains("CURRENT_USER"));
        assertEquals("WITH CHECK must NOT be widened to group/ancestor orgs", 0,
                count("select count(*) from pg_policies where policyname like '%\\_orgs' and (" +
                        "with_check like '%organisation_group_organisation%' " +
                        "or with_check like '%org_ids%' " +
                        "or with_check like '%rls_visible_org_ids%' " +
                        "or with_check like '% ANY %')"));
    }

    @Test
    public void everyOrgScopedPolicyUsesTheSingleEvalInitPlanShape() {
        // V1_406: every *_orgs USING must be `organisation_id = ANY (COALESCE((SELECT <fn>()), '{}'::integer[]))`.
        // Its deparsed qual references the visible-org-ids function AND carries the COALESCE-wrapped sublink (the
        // (SELECT ...) is what makes the function a once-per-query InitPlan Param instead of the per-tuple #1008
        // form; the COALESCE is the fail-closed NULL guard). coalesce() over pg_get_expr makes a NULL-qual policy
        // fail the check instead of vanishing from it.
        assertEquals("every *_orgs policy must be on the InitPlan shape: = ANY (COALESCE((SELECT rls_visible_org_ids...()), '{}'))", 0,
                count("select count(*) from pg_policy p " +
                        "join pg_class c on c.oid = p.polrelid " +
                        "join pg_namespace n on n.oid = c.relnamespace " +
                        "where n.nspname = 'public' and p.polname like '%\\_orgs' " +
                        "and not (coalesce(pg_get_expr(p.polqual, p.polrelid), '') like '%rls_visible_org_ids%' " +
                        "     and coalesce(pg_get_expr(p.polqual, p.polrelid), '') like '%COALESCE%')"));
        // The #1008 regression shape: references the function but WITHOUT the COALESCE-wrapped sublink ->
        // re-evaluated per tuple.
        assertEquals("no *_orgs policy may retain the #1008 per-tuple = ANY(fn()) shape (function without COALESCE)", 0,
                count("select count(*) from pg_policy p " +
                        "join pg_class c on c.oid = p.polrelid " +
                        "join pg_namespace n on n.oid = c.relnamespace " +
                        "where n.nspname = 'public' and p.polname like '%\\_orgs' " +
                        "and coalesce(pg_get_expr(p.polqual, p.polrelid), '') like '%rls_visible_org_ids%' " +
                        "and coalesce(pg_get_expr(p.polqual, p.polrelid), '') not like '%COALESCE%'"));
        // The reverted (V1_405 / pre-17.0) shape: two arms joined by OR.
        assertEquals("no *_orgs policy may retain the reverted OR-based shape", 0,
                count("select count(*) from pg_policies " +
                        "where schemaname = 'public' and policyname like '%\\_orgs' and qual like '% OR %'"));
    }

    @Test
    public void txAndRefPoliciesUseTheCorrectVisibleOrgIdsFunctionVariant() {
        // ref tables are ancestor-visible and must use rls_visible_org_ids_with_ancestors(); tx tables the base
        // rls_visible_org_ids(). A tx table on the ancestor variant would widen visibility to ancestor orgs
        // (cross-tenant read). Each policy must reference exactly one variant...
        List<Map<String, Object>> policies = jdbc().queryForList(
                "select c.relname as table_name, pg_get_expr(p.polqual, p.polrelid) as qual " +
                        "from pg_policy p join pg_class c on c.oid = p.polrelid " +
                        "join pg_namespace n on n.oid = c.relnamespace " +
                        "where n.nspname = 'public' and p.polname like '%\\_orgs'");
        assertFalse("expected some org-scoped policies", policies.isEmpty());
        for (Map<String, Object> policy : policies) {
            String table = String.valueOf(policy.get("table_name"));
            String qual = String.valueOf(policy.get("qual"));
            boolean ref = qual.contains("rls_visible_org_ids_with_ancestors(");
            boolean tx = qual.contains("rls_visible_org_ids(");   // base fn call - the ancestors variant's name continues with '_', not '('
            assertTrue(table + " policy must reference exactly one visible-org-ids variant, was: " + qual, ref ^ tx);
        }
        // ...and the split is correct on known anchors: individual is tx (base), address_level_type is ref (ancestor).
        assertTrue("individual (tx) must use the base rls_visible_org_ids()",
                qualFor("individual").contains("rls_visible_org_ids(")
                        && !qualFor("individual").contains("rls_visible_org_ids_with_ancestors("));
        assertTrue("address_level_type (ref) must use rls_visible_org_ids_with_ancestors()",
                qualFor("address_level_type").contains("rls_visible_org_ids_with_ancestors("));
    }

    private String qualFor(String table) {
        List<String> quals = jdbc().queryForList(
                "select pg_get_expr(p.polqual, p.polrelid) from pg_policy p " +
                        "join pg_class c on c.oid = p.polrelid join pg_namespace n on n.oid = c.relnamespace " +
                        "where n.nspname = 'public' and c.relname = ? and p.polname like '%\\_orgs'", String.class, table);
        assertEquals("expected exactly one *_orgs policy on " + table, 1, quals.size());
        return quals.get(0);
    }
}
