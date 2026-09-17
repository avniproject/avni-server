package org.avni.server.dao;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.User;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    /**
     * EntityApprovalStatus is serialised directly to the client by EntityApprovalStatusController,
     * and its @JsonIgnoreProperties list decides what survives. If observations were ever added to
     * that list the sync payload would silently lose the answers, and the symptom would look like a
     * client bug.
     *
     * Scope: this serialises the entity, not the response. The real payload is an
     * EntityModel<EntityApprovalStatus> assembled by RestControllerResourceProcessor.wrap and the
     * controller's process(). So this catches a @JsonIgnoreProperties or field-level regression, and
     * would NOT catch the endpoint being switched to a contract/DTO that omits observations.
     */
    @Test
    public void observationsAreSerialisedIntoTheSyncPayload() throws Exception {
        Map<String, Object> answers = new HashMap<>();
        answers.put("concept-uuid", "answer-concept-uuid");
        EntityApprovalStatus entityApprovalStatus = new EntityApprovalStatus();
        entityApprovalStatus.setObservations(new ObservationCollection(answers));
        // CHSEntity exposes createdBy/lastModifiedBy as the user's name and dereferences the user
        // without a null check, so a bare entity cannot be serialised at all.
        User user = userRepository.getDefaultSuperAdmin();
        entityApprovalStatus.setCreatedBy(user);
        entityApprovalStatus.setLastModifiedBy(user);

        String json = mapper.writeValueAsString(entityApprovalStatus);

        assertTrue("observations must survive serialisation to the client: " + json,
                json.contains("\"observations\""));
        assertTrue("the answer value must survive serialisation: " + json,
                json.contains("answer-concept-uuid"));
    }
}
