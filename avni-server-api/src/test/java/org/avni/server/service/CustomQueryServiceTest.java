package org.avni.server.service;

import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.web.request.CustomQueryContract;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CustomQueryServiceTest {

    @Test
    public void aBundleCannotAddOrChangeASavedQuery() {
        try {
            new CustomQueryService().processCustomQueries(aSavedQuery());
            fail("a saved query must never be accepted from a bundle, whoever uploads it");
        } catch (AvniAccessException e) {
            assertTrue(e.getMessage().contains("customQueries.json"));
        }
    }

    @Test
    public void anEmptySavedQueriesFileIsIgnored() {
        new CustomQueryService().processCustomQueries(Collections.emptyList());
    }

    private static List<CustomQueryContract> aSavedQuery() {
        CustomQueryContract contract = new CustomQueryContract();
        contract.setUuid("8a1f3c52-0c1e-4b5b-9f0e-2d8f4a7b6c10");
        contract.setName("a query");
        contract.setQuery("select 1");
        return Collections.singletonList(contract);
    }
}
