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
 * Layer 1 - applied-state assertion for V1_398 (indexable RLS org policies, #1008).
 * Verifies, against the migrated catalog, that every org-scoped (`*_orgs`) policy was moved to the
 * index-friendly `organisation_id = ANY(<stable fn>)` shape and that the helper functions have the
 * properties the access-path improvement depends on. Cheap guard that the catalog-driven re-application
 * did not silently miss or mis-classify a table.
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
    public void everyOrgScopedPolicyUsesTheIndexableFunctionWithoutASubquery() {
        assertEquals("no *_orgs policy may remain on the old subquery/OR shape", 0,
                count("select count(*) from pg_policies where policyname like '%\\_orgs' " +
                        "and qual not like '%rls_visible_org_ids%'"));
        assertEquals("no *_orgs policy USING clause may contain a SELECT subquery", 0,
                count("select count(*) from pg_policies where policyname like '%\\_orgs' " +
                        "and upper(qual) like '%SELECT%'"));
    }

    @Test
    public void txAndRefPoliciesUseTheCorrectFunctionVariant() {
        int total = count("select count(*) from pg_policies where policyname like '%\\_orgs'");
        int ref = count("select count(*) from pg_policies where policyname like '%\\_orgs' " +
                "and qual like '%with_ancestors%'");
        int tx = count("select count(*) from pg_policies where policyname like '%\\_orgs' " +
                "and qual like '%rls_visible_org_ids%' and qual not like '%with_ancestors%'");

        assertTrue("there must be tx-shaped policies", tx > 0);
        assertTrue("there must be ref-shaped policies", ref > 0);
        assertEquals("every *_orgs policy must be exactly one of tx / ref", total, tx + ref);

        assertEquals("individual is a tx table (own org + group members, no ancestors)", 0,
                count("select count(*) from pg_policies where policyname = 'individual_orgs' " +
                        "and qual like '%with_ancestors%'"));
        assertEquals("concept is a ref table (own org + ancestors + group members)", 1,
                count("select count(*) from pg_policies where policyname = 'concept_orgs' " +
                        "and qual like '%with_ancestors%'"));
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
}
