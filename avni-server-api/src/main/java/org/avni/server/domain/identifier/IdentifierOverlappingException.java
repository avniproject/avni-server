package org.avni.server.domain.identifier;

import org.avni.server.domain.IdentifierUserAssignment;

import java.util.List;

public class IdentifierOverlappingException extends Exception {
    private final List<IdentifierUserAssignment> otherUserAssignments;

    public IdentifierOverlappingException(List<IdentifierUserAssignment> otherUserAssignments) {
        this.otherUserAssignments = otherUserAssignments;
    }

    @Override
    public String getMessage() {
        String clashingIdentifier = otherUserAssignments.get(0).toString();
        return String.format("Another identifier with overlapping ids - %s", clashingIdentifier);
    }
}
