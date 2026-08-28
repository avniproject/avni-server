package org.avni.server.application;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FormTypeTest {
    @Test
    public void getPrivilegeTypeShouldWorkForAllFormTypes() {
        Arrays.stream(FormType.values()).forEach(formType -> assertNotNull(FormType.getPrivilegeType(formType)));
    }

    @Test
    public void approvalAndRejectionFormsAreEditedUnderTheirOwnPrivileges() {
        assertEquals(PrivilegeType.EditApproval, FormType.getPrivilegeType(FormType.Approval));
        assertEquals(PrivilegeType.EditRejection, FormType.getPrivilegeType(FormType.Rejection));
    }
}
