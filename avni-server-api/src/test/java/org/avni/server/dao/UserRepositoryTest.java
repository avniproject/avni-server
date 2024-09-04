package org.avni.server.dao;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Sql({"/test-data.sql"})
public class UserRepositoryTest extends AbstractControllerIntegrationTest {
    @Autowired
    private UserRepository userRepository;

    @Test
    public void hasPrivilege() {
        setUser("demo-user");
        assertFalse(userRepository.hasPrivilege(PrivilegeType.EditSubjectType.name(), UserContextHolder.getUser().getId()));
    }

    @Test
    public void hasAllPrivileges() {
        setUser("demo-admin");
        assertTrue(userRepository.hasAllPrivileges(UserContextHolder.getUser().getId()));
    }

    @Test
    public void hasNoSubjectPrivilege() {
        setUser("user-no-access");
        assertFalse(userRepository.hasSubjectPrivilege(PrivilegeType.ViewSubject.name(), 1, UserContextHolder.getUser().getId()));
    }
}
