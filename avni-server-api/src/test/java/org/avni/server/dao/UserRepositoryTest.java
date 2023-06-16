package org.avni.server.dao;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.Assert.*;

@Sql({"/test-data.sql"})
public class UserRepositoryTest extends AbstractControllerIntegrationTest {
    @Autowired
    private UserRepository userRepository;

    @Test
    public void hasPrivilege() {
        setUser("demo-user");
        assertFalse(userRepository.hasPrivilege(PrivilegeType.EditSubjectType.name(), 4));
    }

    @Test
    public void hasAllPrivileges() {
        setUser("demo-admin");
        assertTrue(userRepository.hasAllPrivileges(4));
    }
}
