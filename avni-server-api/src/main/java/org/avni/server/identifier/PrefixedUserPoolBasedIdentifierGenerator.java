package org.avni.server.identifier;


import jakarta.transaction.Transactional;
import org.avni.server.dao.IdentifierAssignmentRepository;
import org.avni.server.dao.IdentifierUserAssignmentRepository;
import org.avni.server.domain.IdentifierAssignment;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.IdentifierUserAssignment;
import org.avni.server.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


/**
 * It is assumed here that generation of identifiers for the same user will not happen in parallel.
 * If it does happen, there are database checks that ensure that one of the transactions will fail due to
 * uniqueness constraints. One of the transactions will fail in such scenarios.
 *
 * It is also assumed that every id is pre-assigned to a single user. This is to ensure server does not need to have
 * costly locking mechanisms.
 *
 * We will need to introduce row-locking on the identifier_user_assignment table if we expect parallel calls for
 * identifier generation for the same id.
 */
@Service
public class PrefixedUserPoolBasedIdentifierGenerator {
    private static final String PADDING_STRING = "0";
    private static final Logger logger = LoggerFactory.getLogger(PrefixedUserPoolBasedIdentifierGenerator.class);
    private IdentifierAssignmentRepository identifierAssignmentRepository;
    private IdentifierUserAssignmentRepository identifierUserAssignmentRepository;


    @Autowired
    public PrefixedUserPoolBasedIdentifierGenerator(IdentifierAssignmentRepository identifierAssignmentRepository, IdentifierUserAssignmentRepository identifierUserAssignmentRepository) {
        this.identifierAssignmentRepository = identifierAssignmentRepository;
        this.identifierUserAssignmentRepository = identifierUserAssignmentRepository;
    }

    @Transactional
    public void generateIdentifiers(IdentifierSource identifierSource, User user, String prefix, String deviceId) {
        try {
            List<IdentifierUserAssignment> identifierUserAssignments = prepareIdentifierAssignments(identifierSource, user, prefix);
            if (identifierUserAssignments.isEmpty()) {
                return;
            }

            Long batchGenerationSize = identifierSource.getBatchGenerationSize();
            List<IdentifierAssignment> generatedIdentifiers = new ArrayList<>();
            Set<IdentifierUserAssignment> modifiedAssignments = new HashSet<>();

            for (int i = 0; i < batchGenerationSize; i++) {
                try {
                    IdentifierAssignment identifier = generateSingleIdentifierInternal(identifierSource, user, prefix, deviceId);
                    if (identifier != null) {
                        identifier.setUsed(false);
                        generatedIdentifiers.add(identifier);
                    }

                    if (generatedIdentifiers.size() > 0 && generatedIdentifiers.size() % 100 == 0) {
                        saveIdentifierAssignments(new ArrayList<>(identifierUserAssignments), new ArrayList<>(generatedIdentifiers));
                        generatedIdentifiers.clear();
                    }
                } catch (RuntimeException e) {
                    if (e.getMessage().contains("Not enough identifiers available")) {
                        break;
                    }
                    logger.error("Error generating identifier: {}", e.getMessage(), e);
                    break;
                }
            }

            if (!generatedIdentifiers.isEmpty()) {
                saveIdentifierAssignments(new ArrayList<>(identifierUserAssignments), new ArrayList<>(generatedIdentifiers));
            }

            if (generatedIdentifiers.isEmpty() && getExistingIdentifiers(user, deviceId).isEmpty()) {
                logger.warn("Could not generate any identifiers for user {} and identifier source {}", user.getId(), identifierSource.getId());
            }
        } catch (Exception e) {
            logger.error("Fatal error in generateIdentifiers: {}", e.getMessage(), e);
        }
    }

    public IdentifierAssignment generateSingleIdentifier(IdentifierSource identifierSource, User user, String prefix, String deviceId) {
        try {
            IdentifierAssignment identifierAssignment = generateSingleIdentifierInternal(identifierSource, user, prefix, deviceId);
            identifierAssignment.setUsed(true);

            List<IdentifierUserAssignment> identifierUserAssignments = prepareIdentifierAssignments(identifierSource, user, prefix);
            List<IdentifierAssignment> generatedIdentifiers = new ArrayList<>();
            generatedIdentifiers.add(identifierAssignment);

            saveIdentifierAssignments(identifierUserAssignments, generatedIdentifiers);
            return identifierAssignment;
        } catch (Exception e) {
            logger.error("Fatal error in generateSingleIdentifier: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    private void saveIdentifierAssignments(List<IdentifierUserAssignment> identifierUserAssignments, List<IdentifierAssignment> generatedIdentifiers) {
        saveAssignments(identifierUserAssignments, generatedIdentifiers);
    }

    private String addPaddingIfNecessary(String identifier, IdentifierSource identifierSource) {
        // Get prefix from options
        String prefix = identifierSource.getPrefix();
        int prefixLength = prefix != null ? prefix.length() : 0;

        // Calculate required length for the numeric part
        int requiredNumericLength = identifierSource.getMinLength() - prefixLength;

        // Add padding if necessary
        int lengthOfIdentifier = identifier.length();
        if (lengthOfIdentifier < requiredNumericLength) {
            int paddingLength = requiredNumericLength - lengthOfIdentifier;
            String padding = new String(new char[paddingLength]).replace("\0", PADDING_STRING);
            identifier = padding + identifier;
        }
        return identifier;
    }

    private void sortAssignmentsByStartIdentifier(List<IdentifierUserAssignment> assignments, String prefix) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }

        assignments.sort((a1, a2) -> {
            String start1 = a1.getIdentifierStart().replaceFirst(prefix, "");
            String start2 = a2.getIdentifierStart().replaceFirst(prefix, "");

            try {
                long numericStart1 = Long.parseLong(start1);
                long numericStart2 = Long.parseLong(start2);
                return Long.compare(numericStart1, numericStart2);
            } catch (NumberFormatException e) {
                return start1.compareTo(start2);
            }
        });
    }

    private List<IdentifierUserAssignment> prepareIdentifierAssignments(IdentifierSource identifierSource, User user, String prefix) {
        List<IdentifierUserAssignment> identifierUserAssignments = identifierUserAssignmentRepository.getAllNonExhaustedUserAssignments(user, identifierSource);

        if (identifierUserAssignments.isEmpty()) {
            logger.warn("No non-exhausted identifier assignments found for user {} and identifier source {}", user.getId(), identifierSource.getId());
            return Collections.emptyList();
        }

        sortAssignmentsByStartIdentifier(identifierUserAssignments, prefix);
        return identifierUserAssignments;
    }

    private List<String> getExistingIdentifiers(User user, String deviceId) {
        return identifierAssignmentRepository
                .findByAssignedToAndDeviceIdAndUsedFalseAndIsVoidedFalse(user, deviceId)
                .stream()
                .map(IdentifierAssignment::getIdentifier)
                .collect(Collectors.toList());
    }

    private boolean isAssignmentExhausted(IdentifierUserAssignment assignment) {
        return assignment.getLastAssignedIdentifier() != null &&
                assignment.getLastAssignedIdentifier().equals(assignment.getIdentifierEnd());
    }

    private IdentifierAssignment generateSingleIdentifierInternal(IdentifierSource identifierSource, User user, String prefix, String deviceId) {
        List<IdentifierUserAssignment> identifierUserAssignments = prepareIdentifierAssignments(identifierSource, user, prefix);
        if (identifierUserAssignments.isEmpty()) {
            throw new RuntimeException("Not enough identifiers available. Please contact your admin to get more assigned.");
        }

        List<String> existingIdentifiers = getExistingIdentifiers(user, deviceId);

        NextIdentifierUserAssignment nextIdentifierUserAssignment = new NextIdentifierUserAssignment(identifierUserAssignments, 1L);

        while (nextIdentifierUserAssignment.hasNext()) {
            IdentifierUserAssignment identifierUserAssignment = nextIdentifierUserAssignment.next();
            try {
                IdentifierAssignment identifierAssignment = assignNextIdentifier(identifierUserAssignment, prefix, deviceId);

                if (existingIdentifiers.contains(identifierAssignment.getIdentifier())) {
                    continue;
                }

                return identifierAssignment;
            } catch (RuntimeException e) {
                if (e.getMessage().contains("Not enough identifiers available") && nextIdentifierUserAssignment.getAllAssignments().hasNext()) {
                    continue;
                }
                throw e;
            }
        }

        throw new RuntimeException("Not enough identifiers available. Please contact your admin to get more assigned.");
    }

    private IdentifierAssignment assignNextIdentifier(IdentifierUserAssignment identifierUserAssignment, String prefix, String deviceId) {
        if (isAssignmentExhausted(identifierUserAssignment)) {
            throw new RuntimeException("Not enough identifiers available. Please contact your admin to get more assigned.");
        }

        IdentifierSource identifierSource = identifierUserAssignment.getIdentifierSource();
        long endIdentifier = extractNumericPart(identifierUserAssignment.getIdentifierEnd(), prefix);
        long currentIdentifier = determineStartingIdentifier(identifierUserAssignment, prefix);

        int maxAttempts = 10;
        for (int attempt = 0; attempt < maxAttempts && currentIdentifier <= endIdentifier; attempt++) {
            String formattedIdentifier = formatIdentifier(currentIdentifier, identifierSource, prefix);

            if (!identifierExists(formattedIdentifier, identifierSource)) {
                identifierUserAssignment.setLastAssignedIdentifier(formattedIdentifier);
                return createIdentifierAssignment(identifierSource, formattedIdentifier, currentIdentifier,
                        identifierUserAssignment.getAssignedTo(), deviceId);
            }

            logger.info("Identifier {} already exists, trying next one", formattedIdentifier);
            currentIdentifier++;
        }

        if (currentIdentifier > endIdentifier) {
            identifierUserAssignment.setLastAssignedIdentifier(identifierUserAssignment.getIdentifierEnd());
            throw new RuntimeException("Not enough identifiers available. Please contact your admin to get more assigned.");
        } else {
            throw new RuntimeException("Failed to generate a unique identifier after " + maxAttempts + " attempts");
        }
    }

    private long extractNumericPart(String identifier, String prefix) {
        String numericPart = identifier.replaceFirst(prefix, "");
        return Long.parseLong(numericPart);
    }

    private long determineStartingIdentifier(IdentifierUserAssignment assignment, String prefix) {
        if (assignment.getLastAssignedIdentifier() != null) {
            return extractNumericPart(assignment.getLastAssignedIdentifier(), prefix) + 1;
        } else {
            return extractNumericPart(assignment.getIdentifierStart(), prefix);
        }
    }

    private String formatIdentifier(long numericPart, IdentifierSource identifierSource, String prefix) {
        String formattedNumber = addPaddingIfNecessary(Long.toString(numericPart), identifierSource);
        if (formattedNumber.length() > identifierSource.getMaxLength()) {
            throw new RuntimeException(String.format("Identifier %s exceeds max length %d", formattedNumber, identifierSource.getMaxLength()));
        }
        return prefix + formattedNumber;
    }

    private boolean identifierExists(String identifier, IdentifierSource identifierSource) {
        return identifierAssignmentRepository.existsByIdentifierAndIdentifierSourceAndIsVoidedFalse(identifier, identifierSource);
    }

    private IdentifierAssignment createIdentifierAssignment(IdentifierSource identifierSource, String identifier,
                                                            long numericValue, User assignedTo, String deviceId) {
        IdentifierAssignment identifierAssignment = new IdentifierAssignment(
                identifierSource, identifier, numericValue, assignedTo, deviceId);
        identifierAssignment.assignUUID();
        return identifierAssignment;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    private void saveAssignments(List<IdentifierUserAssignment> userAssignments, List<IdentifierAssignment> identifierAssignments) {
        try {
            identifierUserAssignmentRepository.saveAll(userAssignments);

            List<IdentifierAssignment> validIdentifiers = identifierAssignments.stream()
                    .filter(id -> id != null && id.getIdentifier() != null)
                    .collect(Collectors.toList());

            if (!validIdentifiers.isEmpty()) {
                identifierAssignmentRepository.saveAll(validIdentifiers);
            }
        } catch (DataIntegrityViolationException e) {
            logger.error("Duplicate identifier user assignment error: {}", e.getMessage(), e);
            try {
                identifierUserAssignmentRepository.saveAll(userAssignments);
            } catch (Exception ex) {
                logger.error("Error saving identifier user assignments after error: {}", ex.getMessage(), ex);
            }
        } catch (Exception e) {
            logger.error("Error saving identifier user assignments: {}", e.getMessage(), e);
        }
    }
}
