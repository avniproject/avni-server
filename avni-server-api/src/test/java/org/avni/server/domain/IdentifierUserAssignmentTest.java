package org.avni.server.domain;

import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.identifier.IdentifierGeneratorType;
import org.avni.server.service.builder.identifier.IdentifierSourceBuilder;
import org.avni.server.service.builder.identifier.IdentifierUserAssignmentBuilder;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IdentifierUserAssignmentTest {
    @Test
    public void startShouldBeLessThanEnd() {
        IdentifierSource identifierSource = new IdentifierSourceBuilder().addPrefix("Foo-").setType(IdentifierGeneratorType.userPoolBasedIdentifierGenerator).build();
        assertTrue(new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart("Foo-100").setIdentifierEnd("Foo-200").build().isValid());
        assertFalse(new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart("Foo-200").setIdentifierEnd("Foo-100").build().isValid());

        User userIdPrefix = new UserBuilder().withDefaultValuesForNewEntity().setSettings(new JsonObject().with(UserSettings.ID_PREFIX, "Foo-")).build();
        identifierSource = new IdentifierSourceBuilder().addPrefix("Foo-").setType(IdentifierGeneratorType.userBasedIdentifierGenerator).build();
        assertTrue(new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart("100").setIdentifierEnd("200").setAssignedTo(userIdPrefix).build().isValid());
        assertFalse(new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart("200").setIdentifierEnd("100").setAssignedTo(userIdPrefix).build().isValid());
    }
}
