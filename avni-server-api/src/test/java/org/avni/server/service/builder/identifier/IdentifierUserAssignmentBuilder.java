package org.avni.server.service.builder.identifier;

import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.IdentifierUserAssignment;
import org.avni.server.domain.User;

import java.util.UUID;

public class IdentifierUserAssignmentBuilder {
    private final IdentifierUserAssignment identifierUserAssignment = new IdentifierUserAssignment();

    public IdentifierUserAssignmentBuilder() {
        setUuid(UUID.randomUUID().toString());
    }

    public IdentifierUserAssignmentBuilder setUuid(String uuid) {
        identifierUserAssignment.setUuid(uuid);
        return this;
    }

    public IdentifierUserAssignmentBuilder setIdentifierSource(IdentifierSource identifierSource) {
        identifierUserAssignment.setIdentifierSource(identifierSource);
        return this;
    }

    public IdentifierUserAssignmentBuilder setAssignedTo(User assignedTo) {
        identifierUserAssignment.setAssignedTo(assignedTo);
        return this;
    }

    public IdentifierUserAssignmentBuilder setIdentifierStart(String identifierStart) {
        identifierUserAssignment.setIdentifierStart(identifierStart);
        return this;
    }

    public IdentifierUserAssignmentBuilder setIdentifierEnd(String identifierEnd) {
        identifierUserAssignment.setIdentifierEnd(identifierEnd);
        return this;
    }

    public IdentifierUserAssignmentBuilder setLastAssignedIdentifier(String lastAssignedIdentifier) {
        identifierUserAssignment.setLastAssignedIdentifier(lastAssignedIdentifier);
        return this;
    }

    public IdentifierUserAssignment build() {
        return identifierUserAssignment;
    }
}
