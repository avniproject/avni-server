package org.avni.server.web.impl;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy-path smoke tests for /api/impl/*. Asserts the endpoints respond and
 * the basic contract holds against the existing test-data.sql fixture.
 *
 * TODO: expand to cover the case matrix in the plan
 *   (status filter, locationUuid subtree, pagination, 4xx error responses,
 *   multi-org RLS isolation, voided exclusion).
 *   Requires careful seeding of AddressLevels with lineage, Catchment, User,
 *   EncounterType, Individual, Encounter rows — defer until first manual
 *   smoke pass identifies real bugs worth pinning with tests.
 */
@Sql(value = {"/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class CustomImplControllerIntegrationTest extends AbstractControllerIntegrationTest {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-user");
    }

    @Test
    public void catchmentLocationsReturns200ForSignedInUser() {
        ResponseEntity<String> response = template.getForEntity(
                base + "api/impl/catchmentLocations", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("nodes").contains("rootUuids");
    }

    @Test
    public void encountersWithLocationReturns404ForUnknownEncounterType() {
        ResponseEntity<String> response = template.getForEntity(
                base + "api/impl/encountersWithLocation?encounterType=DefinitelyDoesNotExist",
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void encountersWithLocationReturns400ForInvalidStatus() {
        ResponseEntity<String> response = template.getForEntity(
                base + "api/impl/encountersWithLocation?encounterType=Anything&status=garbage",
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
