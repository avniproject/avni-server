package org.avni.server.dao;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Task 86 follow-up. An organisation login must not hold TRIGGER or TRUNCATE on the shared public tables:
 * a trigger it attaches runs as whoever writes to the table (openchs, across every organisation), and
 * TRUNCATE ignores row level security. It must not write account or account_admin either, since a row in
 * account_admin makes a user an Avni super admin. It keeps read and write on its own rows.
 *
 * Everything runs in one transaction that is rolled back, so the probe roles never outlive the test.
 */
public class OrgLoginTablePrivilegesTest extends AbstractControllerIntegrationTest {
    private static final String NEW_ROLE = "privileges_probe_new";
    private static final String OLD_ROLE = "privileges_probe_old";
    private static final String OWNER_ROLE = "privileges_probe_owner";
    private static final String MIGRATION = "db/migration/V1_410_1__revoke_trigger_truncate_from_org_logins.sql";

    @Autowired
    private DataSource dataSource;

    @Test
    public void newOrgLoginGetsReadWriteButNoTriggerTruncateOrAccountWrites() {
        inRolledBackTransaction(statement -> {
            statement.execute("select create_db_user('" + NEW_ROLE + "', 'password')");
            assertOrgLoginNarrowed(statement, NEW_ROLE);
        });
    }

    @Test
    public void migrationNarrowsAnOrgLoginCreatedWithTheOldGrantsAndIsIdempotent() throws Exception {
        String migration = new String(new ClassPathResource(MIGRATION).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        inRolledBackTransaction(statement -> {
            statement.execute("select create_db_user('" + OLD_ROLE + "', 'password')");
            // What create_db_user granted before this change.
            statement.execute("grant all on all tables in schema public to " + OLD_ROLE);
            statement.execute("grant trigger, truncate on table public.individual to public");
            assertTrue(privilege(statement, OLD_ROLE, "public.individual", "TRIGGER"));
            assertTrue(privilege(statement, OLD_ROLE, "public.account_admin", "INSERT"));

            statement.execute(migration);
            assertOrgLoginNarrowed(statement, OLD_ROLE);
            assertFalse("PUBLIC must lose TRIGGER", publicHolds(statement, "public.individual", "TRIGGER"));

            statement.execute(migration);
            assertOrgLoginNarrowed(statement, OLD_ROLE);
        });
    }

    /**
     * On RDS openchs owns the tables but is not a superuser, and every migration calls grant_all_on_table for
     * each role openchs is a member of, openchs included. The owner must keep write on the account tables.
     */
    @Test
    public void grantAllOnTableRunByANonSuperuserOwnerKeepsTheOwnersWriteOnAccountTables() {
        inRolledBackTransaction(statement -> {
            statement.execute("create role " + OWNER_ROLE + " nosuperuser inherit");
            statement.execute("select create_db_user('" + NEW_ROLE + "', 'password')");
            statement.execute("grant " + NEW_ROLE + " to " + OWNER_ROLE);
            statement.execute("alter table public.account owner to " + OWNER_ROLE);
            statement.execute("alter table public.account_admin owner to " + OWNER_ROLE);
            statement.execute("grant all on all sequences in schema public to " + OWNER_ROLE + " with grant option");
            statement.execute("set local role " + OWNER_ROLE);
            statement.execute("select grant_all_on_table(a.rolname, 'account_admin') from pg_roles a" +
                    " where pg_has_role(current_user, a.oid, 'member')");
            statement.execute("select grant_all_on_table(a.rolname, 'account') from pg_roles a" +
                    " where pg_has_role(current_user, a.oid, 'member')");
            statement.execute("reset role");

            for (String table : new String[]{"public.account_admin", "public.account"}) {
                for (String p : new String[]{"INSERT", "UPDATE", "DELETE"}) {
                    assertTrue("owner must keep " + p + " on " + table, privilege(statement, OWNER_ROLE, table, p));
                    assertFalse(NEW_ROLE + " must not hold " + p + " on " + table, privilege(statement, NEW_ROLE, table, p));
                }
                assertFalse(NEW_ROLE + " must not hold TRIGGER on " + table, privilege(statement, NEW_ROLE, table, "TRIGGER"));
            }
        });
    }

    private void assertOrgLoginNarrowed(Statement statement, String role) throws SQLException {
        for (String p : new String[]{"SELECT", "INSERT", "UPDATE", "DELETE"}) {
            assertTrue(role + " must keep " + p + " on individual", privilege(statement, role, "public.individual", p));
        }
        assertFalse(role + " must not hold TRIGGER on individual", privilege(statement, role, "public.individual", "TRIGGER"));
        assertFalse(role + " must not hold TRUNCATE on individual", privilege(statement, role, "public.individual", "TRUNCATE"));
        assertTrue(role + " must keep SELECT on account_admin", privilege(statement, role, "public.account_admin", "SELECT"));
        for (String table : new String[]{"public.account_admin", "public.account"}) {
            for (String p : new String[]{"INSERT", "UPDATE", "DELETE", "TRUNCATE", "TRIGGER"}) {
                assertFalse(role + " must not hold " + p + " on " + table, privilege(statement, role, table, p));
            }
        }
        assertEquals(role + " must hold TRIGGER on no public table", 0, count(statement,
                "select count(*) from pg_class c where c.relnamespace = 'public'::regnamespace and c.relkind in ('r', 'p', 'v')" +
                        " and has_table_privilege('" + role + "', c.oid, 'TRIGGER')"));
    }

    private boolean privilege(Statement statement, String role, String table, String privilege) throws SQLException {
        try (ResultSet rs = statement.executeQuery(
                "select has_table_privilege('" + role + "', '" + table + "', '" + privilege + "')")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    private boolean publicHolds(Statement statement, String table, String privilege) throws SQLException {
        return count(statement, "select count(*) from aclexplode((select relacl from pg_class where oid = '" + table +
                "'::regclass)) a where a.grantee = 0 and a.privilege_type = '" + privilege + "'") > 0;
    }

    private long count(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private interface StatementWork {
        void run(Statement statement) throws SQLException;
    }

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
