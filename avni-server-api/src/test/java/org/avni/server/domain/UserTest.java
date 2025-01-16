package org.avni.server.domain;

import org.avni.server.domain.factory.UserBuilder;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UserTest {
    @Test
    public void validUserName() throws ValidationException {
        User.validateUsername("test@example", "example");
    }

    @Test
    public void invalidUserName() {
        try {
            User.validateUsername("tes@example", "example");
            Assert.fail("at least four chars in name");
        } catch (ValidationException ignored) {
        }
    }

    @Test
    public void equals() {
        User a = new UserBuilder().withUuid("a").id(2).build();
        User b = new UserBuilder().withUuid("a").id(2).build();
        assertEquals(a, b);
    }
}
