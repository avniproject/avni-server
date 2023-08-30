package org.avni.server.service.identifier;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.IdentifierSourceRepository;
import org.avni.server.dao.IdentifierUserAssignmentRepository;
import org.avni.server.domain.*;
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
public class IdentifierUserAssignmentRepositoryTest extends AbstractControllerIntegrationTest {
    @Autowired
    private IdentifierSourceRepository identifierSourceRepository;
    @Autowired
    private IdentifierUserAssignmentRepository identifierUserAssignmentRepository;
    @Autowired
    private TestOrganisationSetupService organisationSetupService;

    @Test
    public void getOverlappingAssignmentForPooledIdentifier() {
        organisationSetupService.setupOrganisation(this);
        IdentifierSource identifierSource = identifierSourceRepository.save(new IdentifierSourceBuilder().addPrefix("Foo-").setType(IdentifierGeneratorType.userPoolBasedIdentifierGenerator).build());
        User assignmentUser = userRepository.save(new UserBuilder().withDefaultValuesForNewEntity().withAuditUser(organisationSetupService.getUser()).organisationId(organisationSetupService.getUser().getOrganisationId()).build());
        identifierUserAssignmentRepository.save(new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart("Foo-100").setIdentifierEnd("Foo-200").setAssignedTo(assignmentUser).build());
        identifierUserAssignmentRepository.save(new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart("Foo-220").setIdentifierEnd("Foo-300").setAssignedTo(assignmentUser).build());

        assertTrue(hasOverlapPooled(identifierSource, 50, 120));
        assertTrue(hasOverlapPooled(identifierSource, 250, 320));
        assertTrue(hasOverlapPooled(identifierSource, 210, 230));
        assertTrue(hasOverlapPooled(identifierSource, 90, 210));
        assertFalse(hasOverlapPooled(identifierSource, 10, 50));
        assertFalse(hasOverlapPooled(identifierSource, 410, 550));
    }

    @Test
    public void getOverlappingAssignmentForNonPooledIdentifier() {
        organisationSetupService.setupOrganisation(this);
        IdentifierSource identifierSource = identifierSourceRepository.save(new IdentifierSourceBuilder().setType(IdentifierGeneratorType.userBasedIdentifierGenerator).build());
        User assignmentUserUsingSamePrefix = userRepository.save(new UserBuilder().withDefaultValuesForNewEntity().withAuditUser(organisationSetupService.getUser()).organisationId(organisationSetupService.getUser().getOrganisationId()).setSettings(new JsonObject().with(UserSettings.ID_PREFIX, "Foo-")).build());
        User assignmentUserUsingDifferentPrefix = userRepository.save(new UserBuilder().withDefaultValuesForNewEntity().withAuditUser(organisationSetupService.getUser()).organisationId(organisationSetupService.getUser().getOrganisationId()).setSettings(new JsonObject().with(UserSettings.ID_PREFIX, "Bar-")).build());

        identifierUserAssignmentRepository.save(new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart("Foo-100").setIdentifierEnd("Foo-200").setAssignedTo(assignmentUserUsingSamePrefix).build());
        identifierUserAssignmentRepository.save(new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart("Foo-220").setIdentifierEnd("Foo-300").setAssignedTo(assignmentUserUsingSamePrefix).build());

        identifierUserAssignmentRepository.save(new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart("Bar-100").setIdentifierEnd("Bar-200").setAssignedTo(assignmentUserUsingDifferentPrefix).build());
        identifierUserAssignmentRepository.save(new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart("Bar-201").setIdentifierEnd("Bar-400").setAssignedTo(assignmentUserUsingDifferentPrefix).build());

        assertTrue(hasOverlapUserSpecific(identifierSource, assignmentUserUsingSamePrefix, 50, 120));
        assertTrue(hasOverlapUserSpecific(identifierSource, assignmentUserUsingSamePrefix, 250, 320));
        assertTrue(hasOverlapUserSpecific(identifierSource, assignmentUserUsingSamePrefix, 210, 230));
        assertTrue(hasOverlapUserSpecific(identifierSource, assignmentUserUsingSamePrefix, 80, 210));
        assertFalse(hasOverlapUserSpecific(identifierSource, assignmentUserUsingSamePrefix, 10, 50));
        assertFalse(hasOverlapUserSpecific(identifierSource, assignmentUserUsingSamePrefix, 310, 550));
    }

    private boolean hasOverlapPooled(IdentifierSource identifierSource, long start, long end) {
        IdentifierUserAssignment incident = new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart(String.format("Foo-%d", start)).setIdentifierEnd(String.format("Foo-%d", end)).build();
        return identifierUserAssignmentRepository.getOverlappingAssignmentForPooledIdentifier(incident).size() > 0;
    }

    private boolean hasOverlapUserSpecific(IdentifierSource identifierSource, User assignedTo, long start, long end) {
        IdentifierUserAssignment incident = new IdentifierUserAssignmentBuilder().setIdentifierSource(identifierSource).setIdentifierStart(String.format("Foo-%d", start)).setIdentifierEnd(String.format("Foo-%d", end)).setAssignedTo(assignedTo).build();
        return identifierUserAssignmentRepository.getOverlappingAssignmentForNonPooledIdentifier(incident).size() > 0;
    }
}
