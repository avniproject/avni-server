package org.avni.server.dao;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The observations column added by V1_410 (#1051).
 *
 * Nullable with no default is the whole point: a decision that carries no form answers must store
 * SQL NULL, not {}. That is the shape every client which predates the approval/rejection form
 * produces, and it is what keeps "no form was filled" distinguishable from "a form was filled in
 * and left blank". A DEFAULT '{}' here would rewrite that distinction for every organisation.
 */
public class EntityApprovalStatusObservationsColumnTest extends AbstractControllerIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void observationsIsANullableJsonbColumnWithNoDefault() {
        String dataType = jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns " +
                        "where table_name = 'entity_approval_status' and column_name = 'observations'",
                String.class);
        assertEquals("Is the V1_410 migration present?", "jsonb", dataType);

        String isNullable = jdbcTemplate.queryForObject(
                "select is_nullable from information_schema.columns " +
                        "where table_name = 'entity_approval_status' and column_name = 'observations'",
                String.class);
        assertEquals("YES", isNullable);

        String columnDefault = jdbcTemplate.queryForObject(
                "select column_default from information_schema.columns " +
                        "where table_name = 'entity_approval_status' and column_name = 'observations'",
                String.class);
        assertNull("A default would give every existing row a non-null value", columnDefault);
    }
}
