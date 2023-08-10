package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.web.request.EntitySyncStatusContract;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

public class SyncControllerTest extends AbstractControllerIntegrationTest {
    @Autowired
    private SyncController syncController;

    @Test
    public void getSyncDetailsWithScopeAwareEAS() {
        setUser("demo-user");
        List<EntitySyncStatusContract> contracts = SyncEntityName.getEntitiesWithoutSubEntity().stream().map(EntitySyncStatusContract::createForEntityWithoutSubType).collect(Collectors.toList());
        syncController.getSyncDetailsWithScopeAwareEAS(contracts, false);
    }
}
