package org.avni.server.dao;

import org.avni.server.domain.sync.SyncEntityName;
import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SyncParametersTest {
    private static SyncParameters syncParametersFor(SyncEntityName syncEntityName) {
        return new SyncParameters(null, null, null, null, null, null, null, null, syncEntityName, null);
    }

    @Test
    public void onlyTransactionalEntitiesOwnedByOneOrganisationShouldBeFilteredByOrganisation() {
        Set<SyncEntityName> organisationOwned = EnumSet.of(SyncEntityName.Individual, SyncEntityName.ProgramEnrolment, SyncEntityName.Encounter, SyncEntityName.ProgramEncounter);
        Arrays.stream(SyncEntityName.values()).forEach(syncEntityName ->
                assertThat(syncParametersFor(syncEntityName).isOrganisationOwnedTransactionalEntity())
                        .as(syncEntityName.name())
                        .isEqualTo(organisationOwned.contains(syncEntityName)));
    }

    @Test
    public void locationsShouldNotBeFilteredByOrganisationBecauseAncestorOrganisationLocationsAreVisible() {
        assertThat(syncParametersFor(SyncEntityName.AddressLevel).isOrganisationOwnedTransactionalEntity()).isFalse();
        assertThat(syncParametersFor(SyncEntityName.Location).isOrganisationOwnedTransactionalEntity()).isFalse();
    }
}
