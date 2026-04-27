package org.avni.server.domain;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroupTest {

    private Group groupNamed(String name) {
        Group g = new Group();
        g.setName(name);
        return g;
    }

    @Test
    public void administratorsIsADefaultGroup() {
        assertTrue(groupNamed(Group.Administrators).isOneOfTheDefaultGroups());
    }

    @Test
    public void everyoneIsADefaultGroup() {
        assertTrue(groupNamed(Group.Everyone).isOneOfTheDefaultGroups());
    }

    @Test
    public void metabaseUsersIsADefaultGroup() {
        assertTrue(groupNamed(Group.METABASE_USERS).isOneOfTheDefaultGroups());
    }

    @Test
    public void sqliteMigrationIsADefaultGroup() {
        assertTrue(groupNamed(Group.SQLITE_MIGRATION).isOneOfTheDefaultGroups());
    }

    @Test
    public void customGroupIsNotADefaultGroup() {
        assertFalse(groupNamed("Custom Group").isOneOfTheDefaultGroups());
    }

    @Test
    public void sqliteMigrationConstantHasFixedUuid() {
        assertTrue("SQLITE_MIGRATION_UUID must be a non-empty fixed value",
                Group.SQLITE_MIGRATION_UUID != null && !Group.SQLITE_MIGRATION_UUID.isEmpty());
    }
}
