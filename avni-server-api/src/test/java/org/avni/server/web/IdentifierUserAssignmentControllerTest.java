package org.avni.server.web;

import org.avni.server.dao.IdentifierSourceRepository;
import org.avni.server.dao.IdentifierUserAssignmentRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.IdentifierUserAssignment;
import org.avni.server.domain.User;
import org.avni.server.domain.ValidationException;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.identifier.IdentifierGeneratorType;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.builder.identifier.IdentifierSourceBuilder;
import org.avni.server.service.builder.identifier.IdentifierUserAssignmentBuilder;
import org.avni.server.service.identifier.IdentifierUserAssignmentService;
import org.avni.server.web.request.IdentifierUserAssignmentContract;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class IdentifierUserAssignmentControllerTest {
    @Mock private IdentifierUserAssignmentRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private IdentifierSourceRepository identifierSourceRepository;
    @Mock private AccessControlService accessControlService;

    private IdentifierUserAssignmentController controller;
    private IdentifierSource source;
    private User user;
    private IdentifierUserAssignment existing;

    @Before
    public void setup() throws Exception {
        initMocks(this);
        controller = new IdentifierUserAssignmentController(repository, userRepository, identifierSourceRepository,
                accessControlService, new IdentifierUserAssignmentService(repository));
        source = new IdentifierSourceBuilder().addPrefix("Foo-").setType(IdentifierGeneratorType.userPoolBasedIdentifierGenerator).build();
        source.setId(1L);
        source.setUuid("source-uuid");
        user = new UserBuilder().id(10L).userName("alice").build();
        user.setUuid("user-uuid");
        existing = new IdentifierUserAssignmentBuilder().setIdentifierSource(source).setAssignedTo(user)
                .setIdentifierStart("Foo-1").setIdentifierEnd("Foo-100").setLastAssignedIdentifier("Foo-005").build();
        existing.setUuid("iua-uuid");
        when(repository.findByUuid("iua-uuid")).thenReturn(existing);
        when(userRepository.findByUuid("user-uuid")).thenReturn(user);
        when(identifierSourceRepository.findByUuid("source-uuid")).thenReturn(source);
    }

    private IdentifierUserAssignmentContract contract(String start, String end, boolean voided) {
        IdentifierUserAssignmentContract c = new IdentifierUserAssignmentContract();
        c.setUuid("iua-uuid");
        c.setUserUUID("user-uuid");
        c.setIdentifierSourceUUID("source-uuid");
        c.setIdentifierStart(start);
        c.setIdentifierEnd(end);
        c.setVoided(voided);
        return c;
    }

    @Test
    public void rejectsRangeChangeOnAssignmentThatHasIssuedIdentifiers() throws Exception {
        try {
            controller.save(contract("Foo-1", "Foo-500", false));
            fail("Expected ValidationException");
        } catch (ValidationException e) {
            assertTrue(e.getMessage().contains("after identifiers have been issued"));
        }
        assertEquals("Foo-100", existing.getIdentifierEnd());
        verify(repository, never()).save(any());
    }

    @Test
    public void rejectsAssignedUserChangeOnAssignmentThatHasIssuedIdentifiers() throws Exception {
        User other = new UserBuilder().id(11L).userName("bob").build();
        other.setUuid("other-uuid");
        when(userRepository.findByUuid("other-uuid")).thenReturn(other);
        IdentifierUserAssignmentContract c = contract("Foo-1", "Foo-100", false);
        c.setUserUUID("other-uuid");
        try {
            controller.save(c);
            fail("Expected ValidationException");
        } catch (ValidationException e) {
            assertTrue(e.getMessage().contains("after identifiers have been issued"));
        }
        assertEquals(user, existing.getAssignedTo());
        verify(repository, never()).save(any());
    }

    @Test
    public void acceptsIdenticalRepostSoReUploadStaysIdempotent() throws Exception {
        controller.save(contract("Foo-1", "Foo-100", false));
        verify(repository).save(existing);
        assertFalse(existing.isVoided());
    }

    @Test
    public void stillAllowsVoiding() throws Exception {
        controller.save(contract("Foo-1", "Foo-100", true));
        verify(repository).save(existing);
        assertTrue(existing.isVoided());
    }
}
