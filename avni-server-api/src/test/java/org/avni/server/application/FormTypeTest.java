package org.avni.server.application;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertNotNull;

public class FormTypeTest {
    @Test
    public void getPrivilegeTypeShouldWorkForAllFormTypes() {
        Arrays.stream(FormType.values()).forEach(formType -> assertNotNull(FormType.getPrivilegeType(formType)));
    }
}
