package org.avni.server.service.abdm.hiu;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.framework.security.UserContextHolder;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

// F10: every read of received records is one row of abdm_record_read (V1_421): who read, which consent request, when.
@Sql(scripts = {"/test-data.sql"})
@Transactional
public class AbdmRecordReadsIntegrationTest extends AbstractControllerIntegrationTest {
    private static final long DEMO_ORG = 2L;
    private static final long DEMO_USER = 4L;

    @Autowired
    private AbdmRecordReads reads;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;
    @Autowired
    private DataSource dataSource;

    @Test
    public void aReadIsLoggedWithItsUserItsConsentRequestAndItsTime() {
        namedJdbc.getJdbcOperations().update("delete from abdm_record_read");
        setUser("demo-user");

        reads.logged("request-uuid-1");
        reads.logged("request-uuid-1");

        List<Map<String, Object>> rows = namedJdbc.getJdbcOperations().queryForList(
                "select organisation_id, user_id, consent_request_uuid, read_at from abdm_record_read order by id");
        assertEquals(2, rows.size());
        assertEquals(DEMO_ORG, ((Number) rows.get(0).get("organisation_id")).longValue());
        assertEquals(DEMO_USER, ((Number) rows.get(0).get("user_id")).longValue());
        assertEquals("request-uuid-1", rows.get(0).get("consent_request_uuid"));
        assertNotNull(rows.get(0).get("read_at"));
    }

    // No user, no read: a read that cannot say who made it is refused.
    @Test
    public void withNoUserTheReadIsRefused() {
        UserContextHolder.clear();

        assertThrows(IllegalStateException.class, () -> reads.logged("request-uuid-1"));
    }

    // The test data scripts delete users, which a logged read points at, so they clear the reads first.
    @Test
    public void theTestDataScriptRunsOverALoggedRead() {
        aReadIsLogged();

        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource), new ClassPathResource("test-data.sql"));
    }

    @Test
    public void theOpenchsOrganisationTestDataScriptRunsOverALoggedRead() {
        aReadIsLogged();

        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource), new ClassPathResource("test-data-openchs-organisation.sql"));
    }

    private void aReadIsLogged() {
        setUser("demo-user");
        reads.logged("request-uuid-1");
        UserContextHolder.clear();
    }

    // Each organisation sees only its own reads, as with every ABDM table.
    @Test
    public void theLogIsUnderRowLevelSecurity() {
        Boolean rowSecurity = namedJdbc.getJdbcOperations().queryForObject(
                "select relrowsecurity from pg_class where relname = 'abdm_record_read'", Boolean.class);
        Integer policies = namedJdbc.getJdbcOperations().queryForObject(
                "select count(*) from pg_policies where tablename = 'abdm_record_read'", Integer.class);

        assertEquals(Boolean.TRUE, rowSecurity);
        assertTrue(policies > 0);
    }
}
