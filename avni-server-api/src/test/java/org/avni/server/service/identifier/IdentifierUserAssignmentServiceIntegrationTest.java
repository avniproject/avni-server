package org.avni.server.service.identifier;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.IdentifierSourceRepository;
import org.avni.server.dao.IdentifierUserAssignmentRepository;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.IdentifierUserAssignment;
import org.avni.server.domain.User;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.identifier.IdentifierGeneratorType;
import org.avni.server.service.builder.TestOrganisationSetupService;
import org.avni.server.service.builder.identifier.IdentifierSourceBuilder;
import org.avni.server.service.builder.identifier.IdentifierUserAssignmentBuilder;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class IdentifierUserAssignmentServiceIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private IdentifierSourceRepository identifierSourceRepository;
    @Autowired
    private IdentifierUserAssignmentRepository identifierUserAssignmentRepository;
    @Autowired
    private TestOrganisationSetupService organisationSetupService;

    @Test
    public void getOverlappingAssignmentForIdentifierSourceWithPrefix() {
        organisationSetupService.setupOrganisation(this);
        IdentifierSource identifierSource = identifierSourceRepository.save(new IdentifierSourceBuilder().addPrefix("Foo-").setType(IdentifierGeneratorType.userPoolBasedIdentifierGenerator).build());
        User assignmentUser = userRepository.save(new UserBuilder().withDefaultValuesForNewEntity().withAuditUser(organisationSetupService.getUser()).organisationId(organisationSetupService.getUser().getOrganisationId()).build());
        identifierUserAssignmentRepository.save(new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart("Foo-100").setIdentifierEnd("Foo-200").setAssignedTo(assignmentUser).build());
        identifierUserAssignmentRepository.save(new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart("Foo-220").setIdentifierEnd("Foo-300").setAssignedTo(assignmentUser).build());

        assertTrue(hasOverlap(identifierSource, 50, 120));
        assertTrue(hasOverlap(identifierSource, 250, 320));
        assertTrue(hasOverlap(identifierSource, 210, 230));
        assertFalse(hasOverlap(identifierSource, 10, 50));
        assertFalse(hasOverlap(identifierSource, 410, 550));
    }

    private boolean hasOverlap(IdentifierSource identifierSource, long start, long end) {
        IdentifierUserAssignment incident = new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart(String.format("Foo-%d", start)).setIdentifierEnd(String.format("Foo-%d", end)).build();
        return identifierUserAssignmentRepository.getOverlappingAssignmentForIdentifierSourceWithPrefix(incident).size() > 0;
    }
}
