package org.avni.server.rls;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Layer 3 - plan-shape / performance signal for V1_398 (#1008).
 * The test DB is too small for the planner to prefer an index on the real tables, so this builds a
 * representative cross-org probe table (100k rows, 1% in the "small" org) and EXPLAINs the two policy shapes:
 *  - the new `organisation_id = ANY(<stable fn>)` shape must scan via the organisation_id index;
 *  - the old `... OR organisation_id IN (subplan)` shape cannot use that index (the regression V1_398 fixes).
 * Asserts on the index NAME via EXPLAIN (FORMAT JSON) rather than substring-matching the loose token "Index"
 * (which the bigserial PK index would also satisfy). Self-contained and reversible (drops its probe objects).
 */
public class RlsPolicyPlanShapeIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String ORG_INDEX = "rls_plan_probe_org_idx";

    @Autowired
    private DataSource dataSource;

    private String explainJson(JdbcTemplate jdbc, String whereClause) {
        return jdbc.queryForObject(
                "explain (costs off, format json) select count(*) from public.rls_plan_probe where " + whereClause,
                String.class);
    }

    @Test
    public void indexableShapeUsesTheOrgIndexWhileTheOldOrShapeCannot() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("drop table if exists public.rls_plan_probe");
            jdbc.execute("create table public.rls_plan_probe (id bigserial primary key, organisation_id integer not null)");
            jdbc.execute("insert into public.rls_plan_probe (organisation_id) " +
                    "select case when g % 100 = 0 then 200 else 100 end from generate_series(1, 100000) g");
            jdbc.execute("create index " + ORG_INDEX + " on public.rls_plan_probe(organisation_id)");
            jdbc.execute("analyze public.rls_plan_probe");
            // STABLE function with a table-reading body (so it is not constant-folded) returning the small org id,
            // mirroring the production rls_visible_org_ids() shape.
            jdbc.execute("create or replace function public.rls_plan_probe_ids() returns integer[] language sql stable " +
                    "as $$ select array(select 200 from public.organisation limit 1) $$");

            String newPlan = explainJson(jdbc, "organisation_id = ANY (public.rls_plan_probe_ids())");
            String oldPlan = explainJson(jdbc,
                    "organisation_id = 200 or organisation_id in (select organisation_id from public.organisation_group_organisation)");

            assertTrue("new `= ANY(stable fn)` shape must scan via the organisation_id index, but plan was:\n" + newPlan,
                    newPlan.contains(ORG_INDEX));
            assertFalse("new shape must NOT sequentially scan the whole table, but plan was:\n" + newPlan,
                    newPlan.contains("Seq Scan"));
            assertFalse("old `OR ... IN (subplan)` shape must NOT be able to use the organisation_id index " +
                            "(the regression V1_398 fixes), but plan was:\n" + oldPlan,
                    oldPlan.contains(ORG_INDEX));
            assertTrue("old shape is expected to sequentially scan, but plan was:\n" + oldPlan,
                    oldPlan.contains("Seq Scan"));
        } finally {
            jdbc.execute("drop function if exists public.rls_plan_probe_ids()");
            jdbc.execute("drop table if exists public.rls_plan_probe");
        }
    }
}
