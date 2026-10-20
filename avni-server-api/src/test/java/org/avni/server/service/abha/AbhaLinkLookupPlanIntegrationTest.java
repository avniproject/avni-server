package org.avni.server.service.abha;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.AbhaLinkRepository;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Asking who holds an ABHA number must stay an index read at any organisation size, never a scan of subjects or of
 * their observations. Both tests EXPLAIN the repository's own SQL as demo's role (org 2), so the row-level security
 * policies apply as they do on the server, inside a transaction that is rolled back.
 */
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AbhaLinkLookupPlanIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String DEMO_ROLE = "demo";
    private static final long DEMO_ID = 2L;
    private static final String SUBJECT_TYPE = "9f2af1f9-e150-4f8e-aad3-40bb7eb05aa3";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private DataSource dataSource;

    // The shape: with sequential scans priced out, the query can be served by abha_link_org_number_live on both of its
    // columns, and the subject by an index. A query that stopped matching the partial index's predicate, or lost the
    // organisation or number condition, fails here.
    @Test
    public void lookupIsAnIndexReadOnTheLiveNumberIndex() {
        inRolledBackTransaction(statement -> {
            seed(statement, 1000);
            statement.execute("analyze public.individual");
            statement.execute("analyze public.abha_link");
            statement.execute("set local enable_seqscan = off");
            statement.execute("set local role " + DEMO_ROLE);

            JsonNode plan = explainJson(statement, someLiveNumber(statement));

            // An Index Scan carries the index itself; a Bitmap Heap Scan has it on its Bitmap Index Scan child.
            JsonNode link = indexRead(scanOf(plan, "abha_link"));
            assertEquals(plan.toString(), "abha_link_org_number_live", link.path("Index Name").asText());
            assertTrue(plan.toString(), link.path("Index Cond").asText().contains("abha_number"));
            assertTrue(plan.toString(), link.path("Index Cond").asText().contains("organisation_id"));
            assertFalse(plan.toString(), scanOf(plan, "individual").path("Node Type").asText().contains("Seq Scan"));
        });
    }

    // The card's measure: a million subjects in one organisation, answered within half a second, with the planner
    // left to choose. About a minute to seed, so it runs only when asked: AVNI_SCALE_TESTS=1 ./gradlew
    // :avni-server-api:test --tests '*AbhaLinkLookupPlanIntegrationTest'. The plan is printed to the test's output.
    @Test
    public void millionSubjectsAnsweredWithinHalfASecond() {
        Assume.assumeTrue("set AVNI_SCALE_TESTS=1 to seed a million subjects", "1".equals(System.getenv("AVNI_SCALE_TESTS")));
        inRolledBackTransaction(statement -> {
            long seedingStarted = System.currentTimeMillis();
            seed(statement, 1_000_000);
            System.out.println("Seeded 1,000,000 subjects and their links in " + (System.currentTimeMillis() - seedingStarted) / 1000 + " s");
            statement.execute("analyze public.individual");
            statement.execute("analyze public.abha_link");
            statement.execute("set local role " + DEMO_ROLE);
            String number = someLiveNumber(statement);

            long started = System.nanoTime();
            List<String> holders = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery(sql(number))) {
                while (rs.next()) holders.add(rs.getString(1));
            }
            long millis = (System.nanoTime() - started) / 1_000_000;

            StringBuilder analyzed = new StringBuilder();
            try (ResultSet rs = statement.executeQuery("explain (analyze, buffers) " + sql(number))) {
                while (rs.next()) analyzed.append(rs.getString(1)).append('\n');
            }
            System.out.println("ABHA lookup over 1,000,000 subjects in one organisation: " + millis + " ms, " + holders.size() + " holder(s)\n" + analyzed);

            assertEquals(1, holders.size());
            assertTrue(millis + " ms\n" + analyzed, millis < 500);
            assertTrue(analyzed.toString(), analyzed.toString().contains("abha_link_org_number_live"));
            assertFalse(analyzed.toString(), analyzed.toString().contains("Seq Scan"));
        });
    }

    // count subjects in demo, each with one live link holding a distinct 14-digit number; every tenth subject also has
    // a voided link from an earlier answer. The server sends a notice per subject inserted, and the driver keeps every
    // one: at a million the test ran out of memory, so notices are off.
    private static void seed(Statement statement, int count) throws SQLException {
        statement.execute("set local client_min_messages = warning");
        statement.execute("insert into public.individual (uuid, address_id, version, date_of_birth, date_of_birth_verified, first_name, " +
                "last_name, gender_id, organisation_id, subject_type_id, observations, created_by_id, last_modified_by_id, created_date_time, " +
                "last_modified_date_time) " +
                "select md5('abha-scale-' || g)::uuid::text, 1, 1, current_date, false, 'AbhaScale', 'Holder', 1, " + DEMO_ID + ", " +
                "(select id from public.subject_type where uuid = '" + SUBJECT_TYPE + "'), '{}'::jsonb, 1, 1, now(), now() " +
                "from generate_series(1, " + count + ") g");
        statement.execute("insert into public.abha_link (uuid, organisation_id, individual_id, abha_number, is_voided, created_by_id, " +
                "last_modified_by_id, created_date_time, last_modified_date_time) " +
                "select md5('abha-link-' || i.id)::uuid::text, " + DEMO_ID + ", i.id, lpad((10000000000000 + i.id)::text, 14, '0'), false, 1, 1, now(), now() " +
                "from public.individual i where i.first_name = 'AbhaScale'");
        statement.execute("insert into public.abha_link (uuid, organisation_id, individual_id, abha_number, is_voided, created_by_id, " +
                "last_modified_by_id, created_date_time, last_modified_date_time) " +
                "select md5('abha-old-link-' || i.id)::uuid::text, " + DEMO_ID + ", i.id, lpad((20000000000000 + i.id)::text, 14, '0'), true, 1, 1, now(), now() " +
                "from public.individual i where i.first_name = 'AbhaScale' and i.id % 10 = 0");
    }

    private static String someLiveNumber(Statement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery("select l.abha_number from public.abha_link l join public.individual i on i.id = l.individual_id " +
                "where i.first_name = 'AbhaScale' and not l.is_voided order by l.individual_id offset (select count(*) / 2 from public.abha_link where not is_voided) limit 1")) {
            assertTrue("no seeded link", rs.next());
            return rs.getString(1);
        }
    }

    private static String sql(String number) {
        return AbhaLinkRepository.LIVE_SUBJECT_UUIDS
                .replace(":organisationId", String.valueOf(DEMO_ID))
                .replace(":abhaNumber", "'" + number + "'");
    }

    private static JsonNode explainJson(Statement statement, String number) throws SQLException {
        try (ResultSet rs = statement.executeQuery("explain (costs off, format json) " + sql(number))) {
            rs.next();
            try {
                return MAPPER.readTree(rs.getString(1)).get(0).get("Plan");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // The scan node reading the given table, wherever it sits in the plan.
    private static JsonNode scanOf(JsonNode plan, String relation) {
        JsonNode found = find(plan, relation);
        assertNotNull("no scan of " + relation + " in " + plan, found);
        return found;
    }

    private static JsonNode indexRead(JsonNode scan) {
        if (scan.has("Index Name")) return scan;
        for (JsonNode child : scan.path("Plans")) {
            if (child.has("Index Name")) return child;
        }
        return scan;
    }

    private static JsonNode find(JsonNode node, String relation) {
        if (relation.equals(node.path("Relation Name").asText())) return node;
        for (JsonNode child : node.path("Plans")) {
            JsonNode found = find(child, relation);
            if (found != null) return found;
        }
        return null;
    }

    private interface StatementWork {
        void run(Statement statement) throws SQLException;
    }

    // As the server's openchs role, so seeding is not filtered; the work sets demo's role itself. Always rolled back.
    private void inRolledBackTransaction(StatementWork work) {
        new JdbcTemplate(dataSource).execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                work.run(statement);
            } finally {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
            }
            return null;
        });
    }
}
