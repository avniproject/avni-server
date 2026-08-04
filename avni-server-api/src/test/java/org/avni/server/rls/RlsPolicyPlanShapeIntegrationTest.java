package org.avni.server.rls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Layer 3 - plan-shape / performance signal for the org RLS policies (#1008 -> #1029 revert -> #1030 re-do).
 * The test DB is too small for the planner to prefer an index on the real tables, so both tests build a
 * representative cross-org probe table (100k rows, 1% in the "small" org) and EXPLAIN policy shapes against it.
 * The signal is taken from the PROBE relation's own scan node (parsed out of EXPLAIN FORMAT JSON), not from
 * "is there a Seq Scan anywhere": with enable_seqscan=off the planner reads even a non-indexable predicate via
 * a full index scan, so a substring match on "Seq Scan" validates nothing.
 *
 * The first test compares predicate shapes INLINE (in a WHERE clause). The second test is the load-bearing one
 * for #1030: it EXPLAINs through a REAL POLICY under SET ROLE, because policy quals live in securityQuals which
 * the planner treats differently from inline quals - `IN (SELECT unnest(fn()))` index-serves inline but degrades
 * to a per-row SubPlan filter as a policy (the trap that made #1030's original spec a no-op). Only a
 * through-policy EXPLAIN validates what production will actually do.
 */
public class RlsPolicyPlanShapeIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String PROBE_TABLE = "rls_plan_probe";
    private static final String ORG_INDEX = "rls_plan_probe_org_idx";
    private static final String PROBE_ROLE = "rls_plan_probe_user";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private DataSource dataSource;

    @Test
    public void indexableShapeGetsAnOrgIndexBoundWhileTheOldOrShapeDoesNot() {
        new JdbcTemplate(dataSource).execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                createProbe(statement);

                JsonNode newProbe = probeScanNode(explain(statement, "organisation_id = ANY (public.rls_plan_probe_ids())"));
                // Old shape mirrors production: scalar-subquery arm (like `= (select id from organisation ...)`)
                // OR an IN-subplan arm. The OR + hashed subplan is what the org index cannot push.
                JsonNode oldProbe = probeScanNode(explain(statement,
                        "organisation_id = (select 200 from public.organisation limit 1) " +
                                "or organisation_id in (select organisation_id from public.organisation_group_organisation)"));

                assertTrue("`= ANY(stable fn)` shape must scan the probe via an organisation_id Index Cond:\n" + newProbe,
                        subtreeHasFieldContaining(newProbe, "Index Cond", "organisation_id"));
                assertTrue("`= ANY(stable fn)` shape's Index Cond must use the organisation_id index:\n" + newProbe,
                        subtreeHasFieldContaining(newProbe, "Index Name", ORG_INDEX));
                assertFalse("`= ANY(stable fn)` shape must have no non-sargable SubPlan filter left on the probe:\n" + newProbe,
                        subtreeHasFieldContaining(newProbe, "Filter", "SubPlan"));
                // The pre-17.0 regression: the OR/IN-subplan predicate cannot be pushed to the org index, so it
                // survives as a per-row Filter referencing the hashed SubPlan on the probe relation itself.
                assertTrue("old `OR ... IN (subplan)` shape must leave a non-sargable SubPlan filter on the probe:\n" + oldProbe,
                        subtreeHasFieldContaining(oldProbe, "Filter", "SubPlan"));
                assertFalse("old shape must NOT get a sargable organisation_id Index Cond on the probe:\n" + oldProbe,
                        subtreeHasFieldContaining(oldProbe, "Index Cond", "organisation_id"));
            } finally {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
            }
            return null;
        });
    }

    /**
     * The #1030 acceptance signal, measured the only way that is honest for RLS: through a real policy under
     * SET ROLE. Three USING shapes on the same probe + role:
     *  - V1_406 `= ANY (COALESCE((SELECT fn()), '{}'))`: the scalar sublink becomes a once-per-query InitPlan
     *    Param (COALESCE = fail-closed NULL guard) and the qual is an indexable ScalarArrayOpExpr -> org
     *    Index Cond, no SubPlan filter. The improvement.
     *  - #1030's originally-specced `IN (SELECT unnest(fn()))`: index-serves INLINE, but as a securityQual it is
     *    never sublink-pulled-up -> per-row hashed SubPlan filter, no org Index Cond. Ships a no-op.
     *  - the reverted OR-based shape (V1_405, today's prod baseline): SubPlan filter, no org Index Cond.
     * Together these prove V1_406 is a real plan improvement over the baseline and not a re-labelled no-op.
     */
    @Test
    public void initPlanPolicyShapeIndexServesUnderSetRoleWhileUnnestAndOrShapesDoNot() {
        new JdbcTemplate(dataSource).execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                createProbe(statement);
                // A real org-style role (create_db_user grants it to openchs, enabling SET ROLE, and grants table
                // access) - transactional, so the rollback below removes it. RLS applies because the role is not
                // the probe table's owner.
                statement.execute("select create_db_user('" + PROBE_ROLE + "', 'password')");
                statement.execute("alter table public." + PROBE_TABLE + " enable row level security");

                JsonNode initPlanShape = explainThroughPolicy(statement,
                        "organisation_id = ANY (COALESCE((SELECT public.rls_plan_probe_ids()), '{}'::integer[]))");
                JsonNode unnestShape = explainThroughPolicy(statement,
                        "organisation_id IN (SELECT unnest(public.rls_plan_probe_ids()))");
                JsonNode orShape = explainThroughPolicy(statement,
                        "organisation_id = (select 200 from public.organisation limit 1) " +
                                "or organisation_id in (select organisation_id from public.organisation_group_organisation)");

                JsonNode initPlanProbe = probeScanNode(initPlanShape);
                assertTrue("V1_406 policy must reach the probe via an organisation_id Index Cond under SET ROLE:\n" + initPlanShape,
                        subtreeHasFieldContaining(initPlanProbe, "Index Cond", "organisation_id"));
                assertTrue("V1_406 policy's Index Cond must use the organisation_id index:\n" + initPlanShape,
                        subtreeHasFieldContaining(initPlanProbe, "Index Name", ORG_INDEX));
                assertFalse("V1_406 policy must leave no per-row SubPlan filter on the probe:\n" + initPlanShape,
                        subtreeHasFieldContaining(initPlanProbe, "Filter", "SubPlan"));
                assertTrue("V1_406 policy must evaluate the function once via an InitPlan:\n" + initPlanShape,
                        subtreeHasFieldContaining(initPlanShape, "Subplan Name", "InitPlan"));

                JsonNode unnestProbe = probeScanNode(unnestShape);
                assertFalse("the unnest-subquery policy must NOT get an organisation_id Index Cond " +
                                "(securityQuals are never sublink-pulled-up - inline plans do not transfer):\n" + unnestShape,
                        subtreeHasFieldContaining(unnestProbe, "Index Cond", "organisation_id"));
                assertTrue("the unnest-subquery policy must degrade to a per-row SubPlan filter on the probe:\n" + unnestShape,
                        subtreeHasFieldContaining(unnestProbe, "Filter", "SubPlan"));

                JsonNode orProbe = probeScanNode(orShape);
                assertFalse("the reverted OR-based policy (prod baseline) must NOT get an organisation_id Index Cond:\n" + orShape,
                        subtreeHasFieldContaining(orProbe, "Index Cond", "organisation_id"));
                assertTrue("the reverted OR-based policy must leave a per-row SubPlan filter on the probe:\n" + orShape,
                        subtreeHasFieldContaining(orProbe, "Filter", "SubPlan"));
            } finally {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
            }
            return null;
        });
    }

    /**
     * Probe scaffolding shared by both tests: a 100k-row cross-org table (1% in the "small" org 200) with an
     * organisation_id btree, plus a function that mirrors production rls_visible_org_ids() exactly - STABLE, and a
     * non-inlinable array_agg-over-UNION body (a SQL-inlinable array(select ...) would let the planner expand it
     * and change the plan shape). enable_seqscan=off forces the planner to use any usable index, so assertions pin
     * policy SHAPE (can the org index be used as a bound?) rather than a cost-based choice that varies by machine.
     * Everything is created inside the caller's transaction and discarded by its rollback.
     */
    private void createProbe(Statement statement) throws SQLException {
        statement.execute("create table public." + PROBE_TABLE + " (id bigserial primary key, organisation_id integer not null)");
        statement.execute("insert into public." + PROBE_TABLE + " (organisation_id) " +
                "select case when g % 100 = 0 then 200 else 100 end from generate_series(1, 100000) g");
        statement.execute("create index " + ORG_INDEX + " on public." + PROBE_TABLE + "(organisation_id)");
        statement.execute("analyze public." + PROBE_TABLE);
        statement.execute("create function public.rls_plan_probe_ids() returns integer[] language sql stable as $$ " +
                "select coalesce(array_agg(org_id), '{}'::integer[]) from (" +
                "  select 200 as org_id " +
                "  union " +
                "  select organisation_id from public.organisation_group_organisation) v $$");
        statement.execute("set local enable_seqscan = off");
    }

    private String explain(Statement statement, String whereClause) throws SQLException {
        try (ResultSet rs = statement.executeQuery(
                "explain (costs off, format json) select count(*) from public." + PROBE_TABLE + " where " + whereClause)) {
            rs.next();
            return rs.getString(1);
        }
    }

    /** (Re)creates the probe policy with the given USING clause, then EXPLAINs a bare count as the probe role -
     *  so the qual is injected as a securityQual, exactly as production queries experience it. */
    private JsonNode explainThroughPolicy(Statement statement, String usingClause) throws SQLException {
        statement.execute("drop policy if exists rls_plan_probe_orgs on public." + PROBE_TABLE);
        statement.execute("create policy rls_plan_probe_orgs on public." + PROBE_TABLE + " using (" + usingClause + ")");
        statement.execute("set role " + PROBE_ROLE);
        try (ResultSet rs = statement.executeQuery(
                "explain (costs off, format json) select count(*) from public." + PROBE_TABLE)) {
            rs.next();
            return rootPlanNode(rs.getString(1));
        } finally {
            try {
                statement.execute("reset role");
            } catch (SQLException resetFailure) {
                // aborted transaction: the caller's rollback reverts SET ROLE; don't mask the original failure
            }
        }
    }

    /** The root Plan node of an EXPLAIN (FORMAT JSON) document, for whole-plan (not probe-subtree) assertions. */
    private JsonNode rootPlanNode(String planJson) {
        try {
            return MAPPER.readTree(planJson).get(0).get("Plan");
        } catch (Exception e) {
            throw new RuntimeException("could not parse EXPLAIN JSON:\n" + planJson, e);
        }
    }

    /** The scan node for the probe relation, pulled out of the plan so assertions target the probe table's own
     *  access path rather than any node anywhere in the plan (e.g. a subplan table's scan). */
    private JsonNode probeScanNode(JsonNode plan) {
        JsonNode probe = findRelationNode(plan, PROBE_TABLE);
        assertNotNull("plan must contain a scan of " + PROBE_TABLE + ", was:\n" + plan, probe);
        return probe;
    }

    private JsonNode probeScanNode(String planJson) {
        return probeScanNode(rootPlanNode(planJson));
    }

    private JsonNode findRelationNode(JsonNode node, String relation) {
        if (node.has("Relation Name") && relation.equals(node.get("Relation Name").asText())) {
            return node;
        }
        if (node.has("Plans")) {
            for (JsonNode child : node.get("Plans")) {
                JsonNode found = findRelationNode(child, relation);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** True if the subtree rooted at node has a `field` (e.g. "Index Cond" / "Filter") whose text contains needle. */
    private boolean subtreeHasFieldContaining(JsonNode node, String field, String needle) {
        if (node.has(field) && node.get(field).asText().contains(needle)) {
            return true;
        }
        if (node.has("Plans")) {
            for (JsonNode child : node.get("Plans")) {
                if (subtreeHasFieldContaining(child, field, needle)) return true;
            }
        }
        return false;
    }
}
