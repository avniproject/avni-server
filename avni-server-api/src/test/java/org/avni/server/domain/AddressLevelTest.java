package org.avni.server.domain;

import org.avni.server.domain.factory.AddressLevelBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AddressLevelTest {
    @Test
    public void getAddressIds() {
        assertEquals(123, new AddressLevelBuilder().withLineage("123").build().getLineageAddressIds().get(0).longValue());
        assertEquals(456, new AddressLevelBuilder().withLineage("123.456").build().getLineageAddressIds().get(1).longValue());
    }
}
