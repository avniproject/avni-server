package org.avni.server.importer.batch.csv.writer.header;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GroupMemberHeadersTest {
    @Test
    public void sampleFileCarriesRemovalReason_butFilesWithoutItStillLoad() {
        GroupMemberHeaders groupMemberHeaders = new GroupMemberHeaders(null);
        HouseholdMemberHeaders householdMemberHeaders = new HouseholdMemberHeaders(null);

        assertEquals("Removal Reason", last(groupMemberHeaders.getAllHeaders()));
        assertEquals("Removal Reason", last(householdMemberHeaders.getAllHeaders()));
        assertFalse(Arrays.asList(groupMemberHeaders.getAllMandatoryHeaders(null, null)).contains("Removal Reason"));
        assertFalse(Arrays.asList(householdMemberHeaders.getAllMandatoryHeaders(null, null)).contains("Removal Reason"));
        assertEquals(List.of("Household Id", "Member Id", "Is head of household (yes|no)", "Relation name with Head of Household", "Membership Start Date", "Membership End Date"),
                Arrays.asList(householdMemberHeaders.getAllMandatoryHeaders(null, null)));
    }

    private String last(String[] headers) {
        return headers[headers.length - 1];
    }
}
