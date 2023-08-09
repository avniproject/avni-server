package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SyncControllerTest extends AbstractControllerIntegrationTest {
    @Autowired
    private SyncController syncController;

    @Test
    @Ignore
    public void getSyncDetailsWithScopeAwareEAS() {
        syncController.getSyncDetailsWithScopeAwareEAS(null, false);
    }
}
