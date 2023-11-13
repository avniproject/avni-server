package org.avni.server.framework.ehcache;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.User;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.Assert.assertNotEquals;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class CacheStrategyTest extends AbstractControllerIntegrationTest {
    @Autowired
    private ConceptRepository conceptRepository;
    @Autowired
    private TestDataSetupService testDataSetupService;

    @Test
    @Ignore // not working when run from command line suite. works in suite from Intellij.
    public void check() {
        User defaultSuperAdmin = userRepository.getDefaultSuperAdmin();
        setUser(defaultSuperAdmin);

        TestDataSetupService.TestOrganisationData organisation1Data = testDataSetupService.setupOrganisation("exampleA");
        Concept concept11 = new ConceptBuilder().withName("foo").withDataType(ConceptDataType.Text).build();
        conceptRepository.save(concept11);

        setUser(defaultSuperAdmin);

        TestDataSetupService.TestOrganisationData organisation2Data = testDataSetupService.setupOrganisation("exampleB");
        Concept concept12 = new ConceptBuilder().withName("foo").withDataType(ConceptDataType.Text).withUuid(concept11.getUuid()).build();
        conceptRepository.save(concept12);

        setUser(organisation1Data.getUser2());
        Long id1 = conceptRepository.findByUuid(concept11.getUuid()).getId();

        setUser(organisation2Data.getUser2());
        Long id2 = conceptRepository.findByUuid(concept12.getUuid()).getId();

        assertNotEquals(id1, id2);
    }
}
