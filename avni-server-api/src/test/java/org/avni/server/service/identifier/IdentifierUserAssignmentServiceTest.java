package org.avni.server.service.identifier;

import org.avni.server.dao.IdentifierUserAssignmentRepository;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.IdentifierUserAssignment;
import org.avni.server.domain.User;
import org.avni.server.domain.ValidationException;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.identifier.IdentifierGeneratorType;
import org.avni.server.service.builder.identifier.IdentifierSourceBuilder;
import org.avni.server.service.builder.identifier.IdentifierUserAssignmentBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class IdentifierUserAssignmentServiceTest {
    @Mock
    private IdentifierUserAssignmentRepository repository;

    private IdentifierUserAssignmentService service;
    private IdentifierSource identifierSource;
    private User user;

    @Before
    public void setup() {
        initMocks(this);
        service = new IdentifierUserAssignmentService(repository);
        identifierSource = new IdentifierSourceBuilder()
                .addPrefix("Foo-")
                .setType(IdentifierGeneratorType.userPoolBasedIdentifierGenerator)
                .build();
        user = new UserBuilder().id(10L).userName("alice").build();
    }

    @Test
    public void update_blocksIdentifierStartChangeAfterIssuance() throws Exception {
        IdentifierUserAssignment existing = assignment("Foo-1", "Foo-100", "Foo-005", user, identifierSource);
        IdentifierUserAssignment incoming = assignment("Foo-2", "Foo-100", null, user, identifierSource);

        try {
            service.update(existing, incoming);
            fail("Expected ValidationException because identifierStart changed after issuance began");
        } catch (ValidationException e) {
            assertTrue("Error should mention issuance lock: " + e.getMessage(),
                    e.getMessage().contains("after identifiers have been issued"));
        }
        verify(repository, never()).updateExistingWithNew(any(), any());
    }

    @Test
    public void update_blocksIdentifierEndChangeAfterIssuance() throws Exception {
        IdentifierUserAssignment existing = assignment("Foo-1", "Foo-100", "Foo-005", user, identifierSource);
        IdentifierUserAssignment incoming = assignment("Foo-1", "Foo-50", null, user, identifierSource);

        try {
            service.update(existing, incoming);
            fail("Expected ValidationException because identifierEnd changed after issuance began");
        } catch (ValidationException e) {
            assertTrue(e.getMessage().contains("after identifiers have been issued"));
        }
        verify(repository, never()).updateExistingWithNew(any(), any());
    }

    @Test
    public void update_blocksAssignedUserChangeAfterIssuance() throws Exception {
        User otherUser = new UserBuilder().id(11L).userName("bob").build();
        IdentifierUserAssignment existing = assignment("Foo-1", "Foo-100", "Foo-005", user, identifierSource);
        IdentifierUserAssignment incoming = assignment("Foo-1", "Foo-100", null, otherUser, identifierSource);

        try {
            service.update(existing, incoming);
            fail("Expected ValidationException because assignedTo changed after issuance began");
        } catch (ValidationException e) {
            assertTrue(e.getMessage().contains("after identifiers have been issued"));
        }
        verify(repository, never()).updateExistingWithNew(any(), any());
    }

    @Test
    public void update_blocksIdentifierSourceChangeAfterIssuance() throws Exception {
        IdentifierSource otherSource = new IdentifierSourceBuilder()
                .addPrefix("Foo-")
                .setType(IdentifierGeneratorType.userPoolBasedIdentifierGenerator)
                .build();
        otherSource.setId(999L);
        identifierSource.setId(1L);
        IdentifierUserAssignment existing = assignment("Foo-1", "Foo-100", "Foo-005", user, identifierSource);
        IdentifierUserAssignment incoming = assignment("Foo-1", "Foo-100", null, user, otherSource);

        try {
            service.update(existing, incoming);
            fail("Expected ValidationException because identifierSource changed after issuance began");
        } catch (ValidationException e) {
            assertTrue(e.getMessage().contains("after identifiers have been issued"));
        }
        verify(repository, never()).updateExistingWithNew(any(), any());
    }

    @Test
    public void update_allowsVoidingAfterIssuance() throws Exception {
        IdentifierUserAssignment existing = assignment("Foo-1", "Foo-100", "Foo-005", user, identifierSource);
        existing.setVoided(false);
        IdentifierUserAssignment incoming = assignment("Foo-1", "Foo-100", null, user, identifierSource);
        incoming.setVoided(true);
        when(repository.getOverlappingAssignmentForPooledIdentifier(incoming)).thenReturn(Collections.emptyList());
        when(repository.updateExistingWithNew(existing, incoming)).thenReturn(incoming);

        IdentifierUserAssignment result = service.update(existing, incoming);

        assertEquals(incoming, result);
        verify(repository).updateExistingWithNew(existing, incoming);
    }

    @Test
    public void update_allowsAnyFieldChangeBeforeIssuance() throws Exception {
        User otherUser = new UserBuilder().id(11L).userName("bob").build();
        IdentifierUserAssignment existing = assignment("Foo-1", "Foo-100", null, user, identifierSource);
        IdentifierUserAssignment incoming = assignment("Foo-50", "Foo-200", null, otherUser, identifierSource);
        when(repository.getOverlappingAssignmentForPooledIdentifier(incoming)).thenReturn(Collections.emptyList());
        when(repository.updateExistingWithNew(existing, incoming)).thenReturn(incoming);

        IdentifierUserAssignment result = service.update(existing, incoming);

        assertEquals(incoming, result);
        verify(repository).updateExistingWithNew(existing, incoming);
    }

    private IdentifierUserAssignment assignment(String start, String end, String lastAssigned, User assignedTo, IdentifierSource source) {
        return new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(source)
                .setAssignedTo(assignedTo)
                .setIdentifierStart(start)
                .setIdentifierEnd(end)
                .setLastAssignedIdentifier(lastAssigned)
                .build();
    }
}
