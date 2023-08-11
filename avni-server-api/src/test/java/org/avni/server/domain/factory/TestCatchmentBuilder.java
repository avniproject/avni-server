package org.avni.server.domain.factory;

import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Catchment;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

public class TestCatchmentBuilder {
    private final Catchment catchment = new Catchment();

    public TestCatchmentBuilder withUuid(String uuid) {
        catchment.setUuid(uuid);
    	return this;
    }

    public TestCatchmentBuilder withDefaultValuesForNewEntity( ) {
        String s = UUID.randomUUID().toString();
        return withUuid(s).withName(s);
    }

    public TestCatchmentBuilder withName(String name) {
        catchment.setName(name);
    	return this;
    }

    public Catchment build() {
        return catchment;
    }

    public TestCatchmentBuilder withAddressLevels(AddressLevel ... addressLevels) {
        catchment.setAddressLevels(Arrays.stream(addressLevels).collect(Collectors.toSet()));
    	return this;
    }
}
