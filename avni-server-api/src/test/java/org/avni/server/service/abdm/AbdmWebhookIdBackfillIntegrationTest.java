package org.avni.server.service.abdm;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// Eka settings saved before webhook IDs existed get one from V1_417, so their organisation has a webhook address without
// being saved again. The migration has already run here, so its update statement is run again on rows made for the test.
@Sql(scripts = {"/test-data.sql"})
@Transactional
public class AbdmWebhookIdBackfillIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String MIGRATION = "/db/migration/V1_417__AbdmWebhookInboxAndJobRuns.sql";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    public void ekaSettingsWithoutAWebhookIdGetARandomOne_andOnesWithAnIdKeepIt() throws IOException {
        insert("backfill-1", "Eka", "{\"clientId\": \"c\"}");
        insert("backfill-2", "Eka", "{\"clientId\": \"c\", \"webhookId\": \"\"}");
        insert("backfill-3", "Eka", "{\"clientId\": \"c\", \"webhookId\": \"kept-id\"}");
        insert("backfill-4", "Glific", "{\"clientId\": \"c\"}");

        jdbc.update(backfillStatement(), Map.of());

        String first = webhookId("backfill-1");
        String second = webhookId("backfill-2");
        assertTrue(first, first.matches("[0-9a-f]{64}"));
        assertTrue(second, second.matches("[0-9a-f]{64}"));
        assertEquals(2, new HashSet<>(List.of(first, second)).size());
        assertEquals("kept-id", webhookId("backfill-3"));
        assertEquals(null, webhookId("backfill-4"));
    }

    private void insert(String uuid, String system, String config) {
        jdbc.update("insert into external_system_config (organisation_id, uuid, version, created_by_id, last_modified_by_id, created_date_time, " +
                "last_modified_date_time, system_name, config) values (3, :uuid, 0, 1, 1, now(), now(), :system, cast(:config as jsonb))",
                Map.of("uuid", uuid, "system", system, "config", config));
    }

    private String webhookId(String uuid) {
        return jdbc.queryForObject("select config ->> 'webhookId' from external_system_config where uuid = :uuid", Map.of("uuid", uuid), String.class);
    }

    private static String backfillStatement() throws IOException {
        try (InputStream in = AbdmWebhookIdBackfillIntegrationTest.class.getResourceAsStream(MIGRATION)) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int start = sql.indexOf("update external_system_config");
            return sql.substring(start, sql.indexOf(';', start));
        }
    }
}
