package org.avni.server.dao.accessControl;

import jakarta.transaction.Transactional;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.PrivilegeRepository;
import org.avni.server.domain.accessControl.Privilege;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNotEquals;

@Sql(scripts = {"/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class PrivilegeRepositoryTest extends AbstractControllerIntegrationTest {
    @Autowired
    private PrivilegeRepository privilegeRepository;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-admin");
    }

    @Test
    public void typeMatchesTheEnum() {
        Iterable<Privilege> privileges = privilegeRepository.findAll();
        List<Privilege> result = new ArrayList<>();
        privileges.forEach(result::add);
        List<PrivilegeType> privilegeTypes = result.stream().map(Privilege::getType).collect(Collectors.toList());
        assertNotEquals(0, privilegeTypes.size());
        assertNotEquals(null, privilegeTypes.get(0));
    }
}
