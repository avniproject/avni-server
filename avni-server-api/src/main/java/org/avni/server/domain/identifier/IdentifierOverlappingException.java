package org.avni.server.domain.identifier;

import org.avni.server.domain.IdentifierUserAssignment;

import java.util.List;

public class IdentifierOverlappingException extends Exception {
    private final List<IdentifierUserAssignment> otherUserAssignment;

    public IdentifierOverlappingException(List<IdentifierUserAssignment> otherUserAssignment) {
        this.otherUserAssignment = otherUserAssignment;
    }
}
