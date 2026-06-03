package org.avni.server.service.identifier;

import org.avni.server.dao.IdentifierUserAssignmentRepository;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.IdentifierUserAssignment;
import org.avni.server.domain.ValidationException;
import org.avni.server.domain.framework.IdHolder;
import org.avni.server.domain.identifier.IdentifierGeneratorType;
import org.avni.server.domain.identifier.IdentifierOverlappingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class IdentifierUserAssignmentService {
    private final IdentifierUserAssignmentRepository identifierUserAssignmentRepository;

    @Autowired
    public IdentifierUserAssignmentService(IdentifierUserAssignmentRepository identifierUserAssignmentRepository) {
        this.identifierUserAssignmentRepository = identifierUserAssignmentRepository;
    }

    public void save(IdentifierUserAssignment identifierUserAssignment) throws IdentifierOverlappingException, ValidationException {
        identifierUserAssignment.validate();

        IdentifierSource identifierSource = identifierUserAssignment.getIdentifierSource();
        synchronized (identifierSource.getUuid().intern()) {
            List<IdentifierUserAssignment> overlappingWithAssignments;
            if (identifierSource.getType().equals(IdentifierGeneratorType.userPoolBasedIdentifierGenerator)) {
                overlappingWithAssignments = identifierUserAssignmentRepository.getOverlappingAssignmentForPooledIdentifier(identifierUserAssignment);
            } else {
                overlappingWithAssignments = identifierUserAssignmentRepository.getOverlappingAssignmentForNonPooledIdentifier(identifierUserAssignment);
            }
            if (overlappingWithAssignments.size() > 0)
                throw new IdentifierOverlappingException(overlappingWithAssignments);
            identifierUserAssignmentRepository.save(identifierUserAssignment);
        }
    }

    public IdentifierUserAssignment update(IdentifierUserAssignment existingIdentifierUserAssignment, IdentifierUserAssignment newIdentifierUserAssignment) throws IdentifierOverlappingException, ValidationException {
        if (existingIdentifierUserAssignment.getLastAssignedIdentifier() != null
                && nonVoidedFieldsChanged(existingIdentifierUserAssignment, newIdentifierUserAssignment)) {
            throw new ValidationException("Identifier assignment cannot be modified after identifiers have been issued; it can only be voided.");
        }

        newIdentifierUserAssignment.validate();

        IdentifierSource identifierSource = newIdentifierUserAssignment.getIdentifierSource();
        synchronized (identifierSource.getUuid().intern()) {
            List<IdentifierUserAssignment> overlappingWithAssignments;
            if (identifierSource.getType().equals(IdentifierGeneratorType.userPoolBasedIdentifierGenerator)) {
                overlappingWithAssignments = identifierUserAssignmentRepository.getOverlappingAssignmentForPooledIdentifier(newIdentifierUserAssignment);
            } else {
                overlappingWithAssignments = identifierUserAssignmentRepository.getOverlappingAssignmentForNonPooledIdentifier(newIdentifierUserAssignment);
            }
            if (overlappingWithAssignments.size() > 1
                    || (overlappingWithAssignments.size() == 1 && !(overlappingWithAssignments.get(0).getId().equals(existingIdentifierUserAssignment.getId()))))
                throw new IdentifierOverlappingException(overlappingWithAssignments);

            return identifierUserAssignmentRepository.updateExistingWithNew(existingIdentifierUserAssignment, newIdentifierUserAssignment);
        }
    }

    private boolean nonVoidedFieldsChanged(IdentifierUserAssignment existing, IdentifierUserAssignment incoming) {
        return !Objects.equals(existing.getIdentifierStart(), incoming.getIdentifierStart())
                || !Objects.equals(existing.getIdentifierEnd(), incoming.getIdentifierEnd())
                || !sameId(existing.getAssignedTo(), incoming.getAssignedTo())
                || !sameId(existing.getIdentifierSource(), incoming.getIdentifierSource());
    }

    private boolean sameId(IdHolder a, IdHolder b) {
        Long aId = a == null ? null : a.getId();
        Long bId = b == null ? null : b.getId();
        return Objects.equals(aId, bId);
    }
}
