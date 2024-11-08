package org.avni.server.dao;

import jakarta.transaction.Transactional;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.joda.time.DateTime;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql({"/test-data.sql"})
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
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

    @Test
    @Transactional
    public void loadingAndSavingUserShouldNotChangeModifiedAudit() throws InterruptedException {
        User defaultSuperAdmin = userRepository.getDefaultSuperAdmin();
        User user1 = new UserBuilder().withDefaultValuesForNewEntity().userName("user@example").withAuditUser(defaultSuperAdmin).build();
        user1 = userRepository.save(user1);
        DateTime lastModifiedDateTime = user1.getLastModifiedDateTime();
        Thread.sleep(1);
        userRepository.save(user1);
        User loaded = userRepository.findOne(user1.getId());
        assertEquals(lastModifiedDateTime, loaded.getLastModifiedDateTime());
    }
}
