package org.avni.server.domain;

import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.identifier.IdentifierGeneratorType;
import org.avni.server.service.builder.identifier.IdentifierSourceBuilder;
import org.avni.server.service.builder.identifier.IdentifierUserAssignmentBuilder;
import org.junit.Before;
import org.junit.Test;

public class IdentifierUserAssignmentTest {
    private IdentifierSource pooledIdSource;
    private IdentifierSource userBasedIdSource;

    @Before
    public void setup() {
        pooledIdSource = new IdentifierSourceBuilder().addPrefix("Foo-").setType(IdentifierGeneratorType.userPoolBasedIdentifierGenerator).build();
        userBasedIdSource = new IdentifierSourceBuilder().addPrefix("Foo-").setType(IdentifierGeneratorType.userBasedIdentifierGenerator).build();
    }

    @Test
    public void startIsLessThanEnd() throws ValidationException {
        new IdentifierUserAssignmentBuilder().setIdentifierSource(pooledIdSource).setIdentifierStart("Foo-100").setIdentifierEnd("Foo-200").build().validate();

        User user = new UserBuilder().withDefaultValuesForNewEntity().setSettings(new JsonObject().with(UserSettings.ID_PREFIX, "Foo-")).build();
        new IdentifierUserAssignmentBuilder().setIdentifierSource(userBasedIdSource).setIdentifierStart("Foo-100").setIdentifierEnd("Foo-200").setAssignedTo(user).build().validate();
    }

    @Test(expected = ValidationException.class)
    public void startIsGreaterThanEnd_Pooled() throws ValidationException {
        new IdentifierUserAssignmentBuilder().setIdentifierSource(pooledIdSource).setIdentifierStart("Foo-200").setIdentifierEnd("Foo-100").build().validate();
    }

    @Test(expected = ValidationException.class)
    public void startIsGreaterThanEnd_UserBased() throws ValidationException {
        User user = new UserBuilder().withDefaultValuesForNewEntity().setSettings(new JsonObject().with(UserSettings.ID_PREFIX, "Foo-")).build();
        new IdentifierUserAssignmentBuilder().setIdentifierSource(userBasedIdSource).setIdentifierStart("200").setIdentifierEnd("100").setAssignedTo(user).build().validate();
    }

    @Test(expected = ValidationException.class)
    public void startAndEndDontStartWithPrefix() throws ValidationException {
//        new IdentifierUserAssignmentBuilder().setIdentifierSource(pooledIdSource).setIdentifierStart("100").setIdentifierEnd("200").build().validate();
        new IdentifierUserAssignmentBuilder().setIdentifierSource(pooledIdSource).setIdentifierStart("Foo-100").setIdentifierEnd("200").build().validate();
        new IdentifierUserAssignmentBuilder().setIdentifierSource(pooledIdSource).setIdentifierStart("100").setIdentifierEnd("Foo-200").build().validate();
    }

    @Test(expected = ValidationException.class)
    public void startDoesntStartWithPrefix() throws ValidationException {
        new IdentifierUserAssignmentBuilder().setIdentifierSource(pooledIdSource).setIdentifierStart("100").setIdentifierEnd("Foo-200").build().validate();
    }

    @Test(expected = ValidationException.class)
    public void endDoesntStartWithPrefix() throws ValidationException {
        new IdentifierUserAssignmentBuilder().setIdentifierSource(pooledIdSource).setIdentifierStart("Foo-100").setIdentifierEnd("200").build().validate();
    }
}
