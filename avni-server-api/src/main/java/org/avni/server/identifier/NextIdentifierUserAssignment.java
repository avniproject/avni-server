package org.avni.server.identifier;

import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.IdentifierUserAssignment;
import org.avni.server.domain.identifier.IdentifierGeneratorType;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class NextIdentifierUserAssignment {
    private final Iterator<IdentifierUserAssignment> allAssignments;
    private Long batchSize;
    private IdentifierUserAssignment cursor;

    public NextIdentifierUserAssignment(List<IdentifierUserAssignment> identifierUserAssignments, Long initialBatchSize) {
        batchSize = initialBatchSize;
        allAssignments = identifierUserAssignments != null ? identifierUserAssignments.iterator() : Collections.<IdentifierUserAssignment>emptyList().iterator();
        cursor = allAssignments.hasNext() ? allAssignments.next() : null;
        if (cursor != null && hasReachedIdentifierEnd(cursor)) {
            cursor = updateCursor();
        }
    }

    public IdentifierUserAssignment next() {
        if (cursor == null || batchSize <= 0) {
            return null;
        }

        IdentifierUserAssignment current = cursor;
        batchSize--;

        if (hasReachedIdentifierEnd(current)) {
            cursor = updateCursor();
        }

        return current;
    }

    private IdentifierUserAssignment updateCursor() {
        if (allAssignments.hasNext()) {
            IdentifierUserAssignment nextAssignment = allAssignments.next();
            while (hasReachedIdentifierEnd(nextAssignment) && allAssignments.hasNext()) {
                nextAssignment = allAssignments.next();
            }

            if (!hasReachedIdentifierEnd(nextAssignment)) {
                return nextAssignment;
            }
        }
        return null;
    }

    private boolean hasReachedIdentifierEnd(IdentifierUserAssignment assignment) {
        if (assignment.getLastAssignedIdentifier() == null) {
            return false;
        }

        String lastAssignedIdentifier = assignment.getLastAssignedIdentifier();
        String identifierEnd = assignment.getIdentifierEnd();

        IdentifierSource identifierSource = assignment.getIdentifierSource();
        String prefix = identifierSource.getType().equals(IdentifierGeneratorType.userPoolBasedIdentifierGenerator) ? identifierSource.getPrefix() : assignment.getAssignedTo().getUserSettings().getIdPrefix();

        String lastAssignedNumericStr = lastAssignedIdentifier.replaceFirst(prefix, "");
        String endNumericStr = identifierEnd.replaceFirst(prefix, "");

        long lastAssignedNumeric = Long.parseLong(lastAssignedNumericStr);
        long endNumeric = Long.parseLong(endNumericStr);

        return lastAssignedNumeric >= endNumeric;
    }

    public boolean hasNext() {
        if (batchSize <= 0) {
            return false;
        }

        if (cursor == null) {
            return false;
        }

        if (hasReachedIdentifierEnd(cursor)) {
            cursor = updateCursor();
            return cursor != null;
        }

        return true;
    }

    public Iterator<IdentifierUserAssignment> getAllAssignments() {
        return allAssignments;
    }
}
