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
 * The two shape assertions from #1008 - every policy on the `organisation_id = ANY(<stable fn>)` indexable
 * shape, and the correct tx/ref function variant - were RETIRED by the #1029 revert (V1_405), which restored the
 * pre-17.0 OR-based USING clause (`= ANY(stable_fn())` re-evaluated the function per tuple, collapsing prod).
 * #1030 recovers them, updated for the once-per-query `organisation_id IN (SELECT unnest(<fn>))` shape.
 *
 * What remains are shape-agnostic invariants that hold across the revert: the helper functions are retained
 * (unused by the reverted policy, reused by #1030), policy names are canonical, and WITH CHECK still scopes
 * writes to the caller's own org.
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

    // The two shape assertions that lived here - everyOrgScopedPolicyUsesTheIndexableFunctionWithoutASubquery
    // and txAndRefPoliciesUseTheCorrectFunctionVariant - were retired by the #1029 revert (V1_405), which
    // restored the pre-17.0 OR-based USING clause. #1030 recovers them for the `IN (SELECT unnest(<fn>))` shape.

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
