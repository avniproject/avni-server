package org.avni.server.rls;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Layer 3 - plan-shape / performance signal for V1_398 (#1008).
 * The test DB is too small for the planner to prefer an index on the real tables, so this builds a
 * representative cross-org probe table (100k rows, 1% in the "small" org) and EXPLAINs the two policy shapes:
 *  - the new `organisation_id = ANY(<stable fn>)` shape must scan via the organisation_id index;
 *  - the old `... OR organisation_id IN (subplan)` shape cannot use that index (the regression V1_398 fixes).
 * Runs everything in one transaction with `enable_seqscan = off` and rolls back: that pins the policy SHAPE
 * (can the org index be used at all?) deterministically instead of a cost-based, machine-dependent planner
 * choice, and discards the probe table/index/function/stats with no separate cleanup. Asserts on the index
 * NAME via EXPLAIN (FORMAT JSON) rather than the loose token "Index" (which the bigserial PK would also match).
 */
public class RlsPolicyPlanShapeIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String ORG_INDEX = "rls_plan_probe_org_idx";

    @Autowired
    private DataSource dataSource;

    private String explain(Statement statement, String whereClause) throws SQLException {
        try (ResultSet rs = statement.executeQuery(
                "explain (costs off, format json) select count(*) from public.rls_plan_probe where " + whereClause)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    public void indexableShapeUsesTheOrgIndexWhileTheOldOrShapeCannot() {
        new JdbcTemplate(dataSource).execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table public.rls_plan_probe (id bigserial primary key, organisation_id integer not null)");
                statement.execute("insert into public.rls_plan_probe (organisation_id) " +
                        "select case when g % 100 = 0 then 200 else 100 end from generate_series(1, 100000) g");
                statement.execute("create index " + ORG_INDEX + " on public.rls_plan_probe(organisation_id)");
                statement.execute("analyze public.rls_plan_probe");
                // STABLE function with a table-reading body (so it is not constant-folded) returning the small org
                // id, mirroring the production rls_visible_org_ids() shape.
                statement.execute("create function public.rls_plan_probe_ids() returns integer[] language sql stable " +
                        "as $$ select array(select 200 from public.organisation limit 1) $$");
                // Force the planner to avoid a seq scan wherever an index is usable, so the assertions pin whether
                // each policy shape CAN use the org index, not a cost-based choice that varies by machine/PG config.
                statement.execute("set local enable_seqscan = off");

                String newPlan = explain(statement, "organisation_id = ANY (public.rls_plan_probe_ids())");
                String oldPlan = explain(statement,
                        "organisation_id = 200 or organisation_id in (select organisation_id from public.organisation_group_organisation)");

                // The new shape can be served entirely by the org index (no seq scan); the old OR/subplan shape
                // cannot avoid a seq scan even with enable_seqscan off (the hashed subplan is not index-pushable,
                // so the whole table is still scanned - it may additionally bitmap-index the `= 200` literal arm,
                // which is why we assert the residual Seq Scan rather than absence of the index here).
                assertTrue("new `= ANY(stable fn)` shape must scan via the organisation_id index, but plan was:\n" + newPlan,
                        newPlan.contains(ORG_INDEX));
                assertFalse("new shape must NOT sequentially scan the whole table, but plan was:\n" + newPlan,
                        newPlan.contains("Seq Scan"));
                assertTrue("old `OR ... IN (subplan)` shape cannot avoid a seq scan even with enable_seqscan off " +
                                "(the regression V1_398 fixes), but plan was:\n" + oldPlan,
                        oldPlan.contains("Seq Scan"));
            } finally {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
            }
            return null;
        });
    }
}
