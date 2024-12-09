package org.avni.server.service.identifier;

import org.avni.server.dao.IdentifierUserAssignmentRepository;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.IdentifierUserAssignment;
import org.avni.server.domain.ValidationException;
import org.avni.server.domain.identifier.IdentifierGeneratorType;
import org.avni.server.domain.identifier.IdentifierOverlappingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
