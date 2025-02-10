package org.avni.server.web.request;

import org.junit.Test;

import static org.junit.Assert.*;

public class GroupPrivilegeContractTest {
    @Test
    public void checkOptionalBehavior() {
        GroupPrivilegeContract groupPrivilegeContract = new GroupPrivilegeContract();
        assertNull(groupPrivilegeContract.getProgramName());

        groupPrivilegeContract = new GroupPrivilegeContract();
        groupPrivilegeContract.setProgramName(null);
        assertNull(groupPrivilegeContract.getProgramName());

        groupPrivilegeContract = new GroupPrivilegeContract();
        groupPrivilegeContract.setProgramName(null);
        assertNull(groupPrivilegeContract.getProgramName());

        groupPrivilegeContract = new GroupPrivilegeContract();
        groupPrivilegeContract.setProgramName("foo");
        assertEquals("foo", groupPrivilegeContract.getProgramName());
    }
}
