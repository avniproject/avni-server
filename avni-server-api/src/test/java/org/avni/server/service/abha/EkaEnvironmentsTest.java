package org.avni.server.service.abha;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// The Eka environments a server may use, from the deployment value avni.eka.allowedEnvironments (production by default).
public class EkaEnvironmentsTest {
    @Test
    public void aServerAllowingProductionOnlyRefusesTheSandbox() {
        EkaEnvironments production = new EkaEnvironments("production");

        assertTrue(production.allows("production"));
        assertFalse(production.allows("sandbox"));
        assertFalse(production.allows(null));
    }

    @Test
    public void aListAllowsEachEnvironmentNamed_withSpacesIgnored() {
        EkaEnvironments both = new EkaEnvironments(" sandbox , production ");

        assertTrue(both.allows("sandbox"));
        assertTrue(both.allows("production"));
        assertFalse(both.allows("staging"));
    }

    @Test
    public void anEmptyListAllowsNothing() {
        assertFalse(new EkaEnvironments("").allows("production"));
        assertFalse(new EkaEnvironments(null).allows("production"));
    }
}
