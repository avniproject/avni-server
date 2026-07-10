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
 * Layer 3 - plan-shape / performance signal for V1_398 (#1008).
 * The test DB is too small for the planner to prefer an index on the real tables, so this builds a
 * representative cross-org probe table (100k rows, 1% in the "small" org) and EXPLAINs the two policy shapes.
 * The signal is taken from the PROBE relation's own scan node (parsed out of EXPLAIN FORMAT JSON), not from
 * "is there a Seq Scan anywhere": with enable_seqscan=off the planner reads even a non-indexable predicate via
 * a full index scan, and the only literal "Seq Scan" in the old plan is the empty organisation_group_organisation
 * subplan table - so a substring match on "Seq Scan" validates nothing and flips spuriously once that table gets
 * an index. Instead:
 *  - the NEW `organisation_id = ANY(<stable fn>)` shape must carry an Index Cond on organisation_id (a sargable
 *    bound that narrows the probe scan) and NO residual SubPlan filter;
 *  - the OLD `... OR organisation_id IN (subplan)` shape must leave a non-sargable Filter referencing a SubPlan
 *    on the probe (the regression V1_398 fixes) - the OR-with-hashed-subplan that the org index cannot push.
 * The probe deliberately mirrors production: a STABLE array_agg-over-UNION function (non-inlinable, like
 * rls_visible_org_ids()) and a scalar-subquery first arm (like organisation.db_user = current_user), so the old
 * shape genuinely exhibits the non-indexable predicate rather than a planner-friendly literal. Everything runs in
 * one transaction and is rolled back, discarding the probe table/index/function/stats with no separate cleanup.
 */
public class RlsPolicyPlanShapeIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String PROBE_TABLE = "rls_plan_probe";
    private static final String ORG_INDEX = "rls_plan_probe_org_idx";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private DataSource dataSource;

    private String explain(Statement statement, String whereClause) throws SQLException {
        try (ResultSet rs = statement.executeQuery(
                "explain (costs off, format json) select count(*) from public." + PROBE_TABLE + " where " + whereClause)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    public void indexableShapeGetsAnOrgIndexBoundWhileTheOldOrShapeDoesNot() {
        new JdbcTemplate(dataSource).execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table public." + PROBE_TABLE + " (id bigserial primary key, organisation_id integer not null)");
                statement.execute("insert into public." + PROBE_TABLE + " (organisation_id) " +
                        "select case when g % 100 = 0 then 200 else 100 end from generate_series(1, 100000) g");
                statement.execute("create index " + ORG_INDEX + " on public." + PROBE_TABLE + "(organisation_id)");
                statement.execute("analyze public." + PROBE_TABLE);
                // Mirror production rls_visible_org_ids() exactly: STABLE, and a non-inlinable array_agg-over-UNION
                // body (a SQL-inlinable array(select ...) would let the planner expand it and change the plan shape).
                statement.execute("create function public.rls_plan_probe_ids() returns integer[] language sql stable as $$ " +
                        "select coalesce(array_agg(org_id), '{}'::integer[]) from (" +
                        "  select 200 as org_id " +
                        "  union " +
                        "  select organisation_id from public.organisation_group_organisation) v $$");
                // Force the planner to use any usable index, so the assertions pin policy SHAPE (can the org index
                // be used as a bound?) rather than a cost-based choice that varies by machine/PG config.
                statement.execute("set local enable_seqscan = off");

                JsonNode newProbe = probeScanNode(explain(statement, "organisation_id = ANY (public.rls_plan_probe_ids())"));
                // Old shape mirrors production: scalar-subquery arm (like `= (select id from organisation ...)`)
                // OR an IN-subplan arm. The OR + hashed subplan is what the org index cannot push.
                JsonNode oldProbe = probeScanNode(explain(statement,
                        "organisation_id = (select 200 from public.organisation limit 1) " +
                                "or organisation_id in (select organisation_id from public.organisation_group_organisation)"));

                assertTrue("new `= ANY(stable fn)` shape must scan the probe via an organisation_id Index Cond:\n" + newProbe,
                        subtreeHasFieldContaining(newProbe, "Index Cond", "organisation_id"));
                assertTrue("new shape's Index Cond must use the organisation_id index:\n" + newProbe,
                        subtreeHasFieldContaining(newProbe, "Index Name", ORG_INDEX));
                assertFalse("new shape must have no non-sargable SubPlan filter left on the probe:\n" + newProbe,
                        subtreeHasFieldContaining(newProbe, "Filter", "SubPlan"));
                // The regression: the old OR/IN-subplan predicate cannot be pushed to the org index, so it survives
                // as a per-row Filter referencing the hashed SubPlan on the probe relation itself.
                assertTrue("old `OR ... IN (subplan)` shape must leave a non-sargable SubPlan filter on the probe " +
                                "(the regression V1_398 fixes):\n" + oldProbe,
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

    /** The scan node for the probe relation, pulled out of EXPLAIN (FORMAT JSON) so assertions target the probe
     *  table's own access path rather than any node anywhere in the plan (e.g. a subplan table's scan). */
    private JsonNode probeScanNode(String planJson) {
        try {
            JsonNode probe = findRelationNode(MAPPER.readTree(planJson).get(0).get("Plan"), PROBE_TABLE);
            assertNotNull("plan must contain a scan of " + PROBE_TABLE + ", was:\n" + planJson, probe);
            return probe;
        } catch (Exception e) {
            throw new RuntimeException("could not parse EXPLAIN JSON:\n" + planJson, e);
        }
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
