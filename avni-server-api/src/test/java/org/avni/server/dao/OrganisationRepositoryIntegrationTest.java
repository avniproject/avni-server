package org.avni.server.dao;

import jakarta.transaction.Transactional;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ImplementationRepository;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Transactional
public class OrganisationRepositoryIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private ImplementationRepository implementationRepository;

    @Test
    public void createSchema() {
        implementationRepository.createDBUser("impl-db-user", "password");
        implementationRepository.createImplementationSchema("impl-schema", "impl-db-user");
    }
}
