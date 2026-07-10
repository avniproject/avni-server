package org.avni.server.service;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Exercises the real V1_399 migration file (read from the classpath, re-run inside a rolled-back transaction so
 * nothing persists and there is no logic duplication) for the behaviours #871's review required:
 *  - serial-ID lineages convert to UUID lineages;
 *  - an ID owned by an ANCESTOR org resolves (address_level_type is ref/ancestor-visible), not dropped as garbage;
 *  - an ID owned by an unrelated org is dropped (cross-environment safety preserved);
 *  - an out-of-bigint-range all-digit segment is dropped gracefully instead of aborting the migration;
 *  - a second run is idempotent (UUID segments kept verbatim).
 * Runs as the default super admin (no SET ROLE) so the migration sees all orgs, exactly as Flyway runs it.
 */
public class LowestAddressLevelTypeUuidMigrationIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String PARENT_UUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String CHILD_UUID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String FOREIGN_UUID = "cccccccc-cccc-cccc-cccc-cccccccccccc";
    private static final String CFG_UUID = "v1399-test-cfg-uuid";

    // Seeds a parent->child org pair plus an unrelated org, an address_level_type owned by each, and the child's
    // organisation_config carrying legacy ID lineages: [ancestorId.ownId, foreignId, 20-digit-overflow-junk].
    private static final String SEED = "DO $seed$ DECLARE uid BIGINT; cat BIGINT; stat BIGINT; " +
            "org_parent BIGINT; org_child BIGINT; org_other BIGINT; alt_p BIGINT; alt_c BIGINT; alt_o BIGINT; BEGIN " +
            "SELECT id INTO uid FROM users ORDER BY id LIMIT 1; " +
            "SELECT id INTO cat FROM organisation_category LIMIT 1; " +
            "SELECT id INTO stat FROM organisation_status LIMIT 1; " +
            "INSERT INTO organisation(uuid,name,db_user,schema_name,category_id,status_id) " +
            "VALUES('v1399-op','V1399Parent','v1399_p_dbu','v1399p',cat,stat) RETURNING id INTO org_parent; " +
            "INSERT INTO organisation(uuid,name,db_user,schema_name,category_id,status_id,parent_organisation_id) " +
            "VALUES('v1399-oc','V1399Child','v1399_c_dbu','v1399c',cat,stat,org_parent) RETURNING id INTO org_child; " +
            "INSERT INTO organisation(uuid,name,db_user,schema_name,category_id,status_id) " +
            "VALUES('v1399-oo','V1399Other','v1399_o_dbu','v1399o',cat,stat) RETURNING id INTO org_other; " +
            "INSERT INTO address_level_type(name,organisation_id,version,uuid,created_by_id,last_modified_by_id,created_date_time,last_modified_date_time,level) " +
            "VALUES('V1399P',org_parent,1,'" + PARENT_UUID + "',uid,uid,now(),now(),2) RETURNING id INTO alt_p; " +
            "INSERT INTO address_level_type(name,organisation_id,version,uuid,created_by_id,last_modified_by_id,created_date_time,last_modified_date_time,level) " +
            "VALUES('V1399C',org_child,1,'" + CHILD_UUID + "',uid,uid,now(),now(),1) RETURNING id INTO alt_c; " +
            "INSERT INTO address_level_type(name,organisation_id,version,uuid,created_by_id,last_modified_by_id,created_date_time,last_modified_date_time,level) " +
            "VALUES('V1399O',org_other,1,'" + FOREIGN_UUID + "',uid,uid,now(),now(),1) RETURNING id INTO alt_o; " +
            "INSERT INTO organisation_config(uuid,organisation_id,created_by_id,last_modified_by_id,created_date_time,last_modified_date_time,settings) " +
            "VALUES('" + CFG_UUID + "',org_child,uid,uid,now(),now(), jsonb_build_object('lowestAddressLevelType', " +
            "jsonb_build_array(alt_p::text||'.'||alt_c::text, alt_o::text, '99999999999999999999'))); END $seed$;";

    @Autowired
    private DataSource dataSource;

    @Test
    public void convertsIdsToUuidsResolvingAncestorsDroppingForeignAndOverflowIdempotently() {
        setUser(userRepository.getDefaultSuperAdmin());
        String migrationSql = readMigration();

        new JdbcTemplate(dataSource).execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute(SEED);

                statement.execute(migrationSql);
                String afterFirst = lowestAddressLevelType(statement);
                // ancestor-owned + own id resolved into one UUID lineage; foreign id and overflow junk dropped.
                assertEquals("ancestor + own ids must resolve to a single UUID lineage; foreign and overflow dropped",
                        "[\"" + PARENT_UUID + "." + CHILD_UUID + "\"]", afterFirst);
                assertTrue("ancestor-owned id must resolve, not be dropped as garbage", afterFirst.contains(PARENT_UUID));
                assertFalse("an unrelated org's id must be dropped (cross-env safety)", afterFirst.contains(FOREIGN_UUID));

                statement.execute(migrationSql);
                assertEquals("second run must be idempotent", afterFirst, lowestAddressLevelType(statement));
            } finally {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
            }
            return null;
        });
    }

    private String lowestAddressLevelType(Statement statement) throws java.sql.SQLException {
        try (ResultSet rs = statement.executeQuery(
                "select settings->'lowestAddressLevelType' from organisation_config where uuid = '" + CFG_UUID + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private String readMigration() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("db/migration/V1_399__StoreLowestAddressLevelTypeAsUuid.sql").getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("could not read V1_399 migration", e);
        }
    }
}
