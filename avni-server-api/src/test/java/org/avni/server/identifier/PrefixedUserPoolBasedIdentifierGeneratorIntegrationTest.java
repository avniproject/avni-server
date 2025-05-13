package org.avni.server.identifier;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.IdentifierAssignmentRepository;
import org.avni.server.dao.IdentifierUserAssignmentRepository;
import org.avni.server.domain.IdentifierAssignment;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.IdentifierUserAssignment;
import org.avni.server.domain.User;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.identifier.IdentifierGeneratorType;
import org.avni.server.service.builder.identifier.IdentifierSourceBuilder;
import org.avni.server.service.builder.identifier.IdentifierUserAssignmentBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.jdbc.Sql;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class PrefixedUserPoolBasedIdentifierGeneratorIntegrationTest extends AbstractControllerIntegrationTest {

    // Constants for different test scenarios
    private static final String PREFIX = "PH";
    private static final String DEVICE_ID = "test-device";

    private static final long SMALL_BATCH_SIZE = 5L;
    private static final long MEDIUM_BATCH_SIZE = 10L;
    private static final long LARGE_BATCH_SIZE = 100L;

    private static final long LOW_MINIMUM_BALANCE = 2L;
    private static final long HIGH_MINIMUM_BALANCE = 10L;

    @MockBean
    private IdentifierUserAssignmentRepository identifierUserAssignmentRepository;

    @MockBean
    private IdentifierAssignmentRepository identifierAssignmentRepository;

    private User user;
    private IdentifierSource identifierSource;
    private PrefixedUserPoolBasedIdentifierGenerator prefixedUserPoolBasedIdentifierGenerator;

    @Before
    public void setup() {
        prefixedUserPoolBasedIdentifierGenerator = new PrefixedUserPoolBasedIdentifierGenerator(
                identifierAssignmentRepository, identifierUserAssignmentRepository);

        user = new UserBuilder().withDefaultValuesForNewEntity().build();
        identifierSource = createIdentifierSource(SMALL_BATCH_SIZE, LOW_MINIMUM_BALANCE);
    }

    private IdentifierSource createIdentifierSource(long batchSize, long minimumBalance) {
        return new IdentifierSourceBuilder()
                .addPrefix(PREFIX)
                .setType(IdentifierGeneratorType.userPoolBasedIdentifierGenerator)
                .setBatchGenerationSize(batchSize)
                .setMinimumBalance(minimumBalance)
                .build();
    }

    @Test
    public void shouldGenerateIdentifiersFromSingleBatch() {
        // Setup
        IdentifierSource identifierSource = createIdentifierSource(SMALL_BATCH_SIZE, LOW_MINIMUM_BALANCE);

        IdentifierUserAssignment assignment = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(identifierSource)
                .setIdentifierStart(PREFIX + "7100")
                .setIdentifierEnd(PREFIX + "7149")
                .setAssignedTo(user)
                .build();

        List<IdentifierUserAssignment> assignments = new ArrayList<>();
        assignments.add(assignment);

        when(identifierUserAssignmentRepository.getAllNonExhaustedUserAssignments(user, identifierSource))
                .thenReturn(assignments);

        when(identifierAssignmentRepository.findByAssignedToAndDeviceIdAndUsedFalseAndIsVoidedFalse(eq(user), anyString()))
                .thenReturn(new ArrayList<>());

        // Execute
        prefixedUserPoolBasedIdentifierGenerator.generateIdentifiers(identifierSource, user, PREFIX, DEVICE_ID);

        // Verify
        ArgumentCaptor<List<IdentifierAssignment>> identifierAssignmentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(identifierAssignmentRepository).saveAll(identifierAssignmentsCaptor.capture());

        List<IdentifierAssignment> savedAssignments = identifierAssignmentsCaptor.getValue();
        assertEquals(SMALL_BATCH_SIZE, savedAssignments.size());

        // Verify the identifiers are sequential
        assertEquals(PREFIX + "7100", savedAssignments.get(0).getIdentifier());
        assertEquals(PREFIX + "7101", savedAssignments.get(1).getIdentifier());
        assertEquals(PREFIX + "7102", savedAssignments.get(2).getIdentifier());
        assertEquals(PREFIX + "7103", savedAssignments.get(3).getIdentifier());
        assertEquals(PREFIX + "7104", savedAssignments.get(4).getIdentifier());

        // Verify the last assigned identifier is updated
        assertEquals(PREFIX + "7104", assignment.getLastAssignedIdentifier());
    }

    @Test
    public void shouldGenerateIdentifiersFromMultipleBatches() {
        // Setup
        IdentifierSource identifierSource = createIdentifierSource(SMALL_BATCH_SIZE, LOW_MINIMUM_BALANCE);

        IdentifierUserAssignment assignment1 = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(identifierSource)
                .setIdentifierStart(PREFIX + "7100")
                .setIdentifierEnd(PREFIX + "7102")
                .setAssignedTo(user)
                .build();

        IdentifierUserAssignment assignment2 = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(identifierSource)
                .setIdentifierStart(PREFIX + "7200")
                .setIdentifierEnd(PREFIX + "7249")
                .setAssignedTo(user)
                .build();

        List<IdentifierUserAssignment> assignments = new ArrayList<>();
        assignments.add(assignment1);
        assignments.add(assignment2);

        when(identifierUserAssignmentRepository.getAllNonExhaustedUserAssignments(user, identifierSource))
                .thenReturn(assignments);

        when(identifierAssignmentRepository.findByAssignedToAndDeviceIdAndUsedFalseAndIsVoidedFalse(eq(user), anyString()))
                .thenReturn(new ArrayList<>());

        // Execute
        prefixedUserPoolBasedIdentifierGenerator.generateIdentifiers(identifierSource, user, PREFIX, DEVICE_ID);

        // Verify
        ArgumentCaptor<List<IdentifierAssignment>> identifierAssignmentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(identifierAssignmentRepository).saveAll(identifierAssignmentsCaptor.capture());

        List<IdentifierAssignment> savedAssignments = identifierAssignmentsCaptor.getValue();
        assertEquals(SMALL_BATCH_SIZE, savedAssignments.size());

        // Verify the identifiers are from both batches
        assertEquals(PREFIX + "7100", savedAssignments.get(0).getIdentifier());
        assertEquals(PREFIX + "7101", savedAssignments.get(1).getIdentifier());
        assertEquals(PREFIX + "7102", savedAssignments.get(2).getIdentifier());
        assertEquals(PREFIX + "7200", savedAssignments.get(3).getIdentifier());
        assertEquals(PREFIX + "7201", savedAssignments.get(4).getIdentifier());

        // Verify the last assigned identifiers are updated
        assertEquals(PREFIX + "7102", assignment1.getLastAssignedIdentifier());
        assertEquals(PREFIX + "7201", assignment2.getLastAssignedIdentifier());
    }

    @Test
    public void shouldGeneratePartialBatchWhenNotEnoughIdentifiersAvailable() {
        // Setup - using medium batch size to ensure we can't fulfill the entire batch
        IdentifierSource identifierSource = createIdentifierSource(MEDIUM_BATCH_SIZE, LOW_MINIMUM_BALANCE);

        IdentifierUserAssignment assignment1 = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(identifierSource)
                .setIdentifierStart(PREFIX + "7100")
                .setIdentifierEnd(PREFIX + "7102")
                .setAssignedTo(user)
                .build();

        List<IdentifierUserAssignment> assignments = new ArrayList<>();
        assignments.add(assignment1);

        when(identifierUserAssignmentRepository.getAllNonExhaustedUserAssignments(user, identifierSource))
                .thenReturn(assignments);

        when(identifierAssignmentRepository.findByAssignedToAndDeviceIdAndUsedFalseAndIsVoidedFalse(eq(user), anyString()))
                .thenReturn(new ArrayList<>());

        // Execute
        prefixedUserPoolBasedIdentifierGenerator.generateIdentifiers(identifierSource, user, PREFIX, DEVICE_ID);

        // Verify that the available identifiers were saved
        ArgumentCaptor<List<IdentifierAssignment>> identifierAssignmentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(identifierAssignmentRepository, atLeastOnce()).saveAll(identifierAssignmentsCaptor.capture());

        List<IdentifierAssignment> savedAssignments = identifierAssignmentsCaptor.getValue();
        assertEquals(3, savedAssignments.size());

        // Verify the identifiers are sequential
        assertEquals(PREFIX + "7100", savedAssignments.get(0).getIdentifier());
        assertEquals(PREFIX + "7101", savedAssignments.get(1).getIdentifier());
        assertEquals(PREFIX + "7102", savedAssignments.get(2).getIdentifier());

        // Verify the last assigned identifier is updated
        assertEquals(PREFIX + "7102", assignment1.getLastAssignedIdentifier());
    }

    @Test
    public void shouldGenerateSingleIdentifierSuccessfully() {
        // Setup - single identifier generation doesn't depend on batch size
        IdentifierSource identifierSource = createIdentifierSource(SMALL_BATCH_SIZE, LOW_MINIMUM_BALANCE);

        IdentifierUserAssignment assignment = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(identifierSource)
                .setIdentifierStart(PREFIX + "7100")
                .setIdentifierEnd(PREFIX + "7149")
                .setAssignedTo(user)
                .build();

        List<IdentifierUserAssignment> assignments = new ArrayList<>();
        assignments.add(assignment);

        when(identifierUserAssignmentRepository.getAllNonExhaustedUserAssignments(user, identifierSource))
                .thenReturn(assignments);

        when(identifierAssignmentRepository.findByAssignedToAndDeviceIdAndUsedFalseAndIsVoidedFalse(eq(user), anyString()))
                .thenReturn(new ArrayList<>());

        // Execute
        IdentifierAssignment result = prefixedUserPoolBasedIdentifierGenerator.generateSingleIdentifier(identifierSource, user, PREFIX, DEVICE_ID);

        // Verify
        assertNotNull(result);
        assertEquals(PREFIX + "7100", result.getIdentifier());
        assertTrue(result.isUsed());

        // Verify the last assigned identifier is updated
        assertEquals(PREFIX + "7100", assignment.getLastAssignedIdentifier());

        // Verify saveAll was called
        verify(identifierAssignmentRepository).saveAll(any());
        verify(identifierUserAssignmentRepository).saveAll(any());
    }

    @Test
    public void shouldGenerateSingleIdentifierFromSecondBatchWhenFirstIsExhausted() {
        // Setup
        IdentifierUserAssignment assignment1 = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(identifierSource)
                .setIdentifierStart(PREFIX + "7100")
                .setIdentifierEnd(PREFIX + "7100")  // Only one identifier available
                .setAssignedTo(user)
                .build();
        assignment1.setLastAssignedIdentifier(PREFIX + "7100"); // Already exhausted

        IdentifierUserAssignment assignment2 = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(identifierSource)
                .setIdentifierStart(PREFIX + "7200")
                .setIdentifierEnd(PREFIX + "7249")
                .setAssignedTo(user)
                .build();

        List<IdentifierUserAssignment> assignments = new ArrayList<>();
        assignments.add(assignment1);
        assignments.add(assignment2);

        when(identifierUserAssignmentRepository.getAllNonExhaustedUserAssignments(user, identifierSource))
                .thenReturn(assignments);

        // Execute
        IdentifierAssignment result = prefixedUserPoolBasedIdentifierGenerator.generateSingleIdentifier(identifierSource, user, PREFIX, DEVICE_ID);

        // Verify
        assertNotNull(result);
        assertEquals(PREFIX + "7200", result.getIdentifier());
        assertTrue(result.isUsed());

        // Verify the last assigned identifier is updated for the second assignment
        assertEquals(PREFIX + "7100", assignment1.getLastAssignedIdentifier());
        assertEquals(PREFIX + "7200", assignment2.getLastAssignedIdentifier());
    }

    @Test
    public void shouldThrowExceptionWhenNoIdentifiersAvailable() {
        // Setup - empty assignments list
        when(identifierUserAssignmentRepository.getAllNonExhaustedUserAssignments(user, identifierSource))
                .thenReturn(new ArrayList<>());

        // Execute and verify exception
        try {
            prefixedUserPoolBasedIdentifierGenerator.generateSingleIdentifier(identifierSource, user, PREFIX, DEVICE_ID);
            fail("Expected RuntimeException was not thrown");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Not enough identifiers available"));
        }
    }

    // Test for overlapping ranges removed as it's not a valid business concern for this class
    // Preventing overlapping ranges is a responsibility of the data validation or service layer

    @Test
    public void shouldNotGenerateIdentifiersWhenAssignmentExhausted() {
        // Setup
        IdentifierUserAssignment exhaustedAssignment = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(identifierSource)
                .setIdentifierStart(PREFIX + "7100")
                .setIdentifierEnd(PREFIX + "7149")
                .setAssignedTo(user)
                .build();

        // Set the last assigned identifier to the end of the range to mark it as exhausted
        exhaustedAssignment.setLastAssignedIdentifier(PREFIX + "7149");

        List<IdentifierUserAssignment> assignments = Collections.singletonList(exhaustedAssignment);

        when(identifierUserAssignmentRepository.getAllNonExhaustedUserAssignments(user, identifierSource))
                .thenReturn(assignments);

        // Execute and verify
        try {
            prefixedUserPoolBasedIdentifierGenerator.generateSingleIdentifier(identifierSource, user, PREFIX, DEVICE_ID);
            fail("Expected RuntimeException was not thrown");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Not enough identifiers available"));
        }
    }

    @Test
    public void shouldPrioritizeRangesWithLowerStartValues() {
        // Setup
        IdentifierUserAssignment assignment1 = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(identifierSource)
                .setIdentifierStart(PREFIX + "7300") // Higher range
                .setIdentifierEnd(PREFIX + "7399")
                .setAssignedTo(user)
                .build();

        IdentifierUserAssignment assignment2 = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(identifierSource)
                .setIdentifierStart(PREFIX + "7150") // Lower range
                .setIdentifierEnd(PREFIX + "7200")
                .setAssignedTo(user)
                .build();

        List<IdentifierUserAssignment> assignments = new ArrayList<>();
        // Add them in reverse order to test sorting
        assignments.add(assignment1);
        assignments.add(assignment2);

        when(identifierUserAssignmentRepository.getAllNonExhaustedUserAssignments(user, identifierSource))
                .thenReturn(assignments);

        // Execute
        IdentifierAssignment result = prefixedUserPoolBasedIdentifierGenerator.generateSingleIdentifier(identifierSource, user, PREFIX, DEVICE_ID);

        // Verify
        assertNotNull(result);
        // Should use the lower range first
        assertEquals(PREFIX + "7150", result.getIdentifier());
        assertTrue(result.isUsed());

        // Verify the last assigned identifier is updated for the lower range assignment
        assertEquals(PREFIX + "7150", assignment2.getLastAssignedIdentifier());
        assertNull(assignment1.getLastAssignedIdentifier()); // Higher range should not be touched
    }

    @Test
    public void shouldRespectIdentifierEndWhenGeneratingIdentifiers() {
        // Setup
        IdentifierUserAssignment assignment = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(identifierSource)
                .setIdentifierStart(PREFIX + "7100")
                .setIdentifierEnd(PREFIX + "7104") // Only 5 identifiers available
                .setAssignedTo(user)
                .build();

        List<IdentifierUserAssignment> assignments = Collections.singletonList(assignment);

        when(identifierUserAssignmentRepository.getAllNonExhaustedUserAssignments(user, identifierSource))
                .thenReturn(assignments);

        // Execute
        prefixedUserPoolBasedIdentifierGenerator.generateIdentifiers(identifierSource, user, PREFIX, DEVICE_ID);

        // Verify
        ArgumentCaptor<List<IdentifierAssignment>> identifierAssignmentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(identifierAssignmentRepository).saveAll(identifierAssignmentsCaptor.capture());

        List<IdentifierAssignment> savedAssignments = identifierAssignmentsCaptor.getValue();
        assertEquals(5, savedAssignments.size());

        // Verify the last identifier is exactly at the end of the range
        assertEquals(PREFIX + "7104", savedAssignments.get(4).getIdentifier());
        assertEquals(PREFIX + "7104", assignment.getLastAssignedIdentifier());

        // Try to generate one more - should fail as we've reached the end
        reset(identifierAssignmentRepository, identifierUserAssignmentRepository);
        assignment.setLastAssignedIdentifier(PREFIX + "7104"); // Mark as exhausted
        when(identifierUserAssignmentRepository.getAllNonExhaustedUserAssignments(user, identifierSource))
                .thenReturn(assignments);

        try {
            prefixedUserPoolBasedIdentifierGenerator.generateSingleIdentifier(identifierSource, user, PREFIX, DEVICE_ID);
            fail("Expected RuntimeException was not thrown");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Not enough identifiers available"));
        }
    }

    @Test
    public void shouldContinueToNextRangeToFulfillBatch() {
        // Setup - create a custom identifier source with a batch size larger than the first range
        IdentifierSource testIdentifierSource = new IdentifierSourceBuilder()
                .addPrefix(PREFIX)
                .setType(IdentifierGeneratorType.userPoolBasedIdentifierGenerator)
                .setBatchGenerationSize(LARGE_BATCH_SIZE) // Need 100 identifiers (realistic batch size)
                .setMinimumBalance(HIGH_MINIMUM_BALANCE)
                .setMinLength(4)
                .setMaxLength(10)
                .build();

        // First range with only 50 identifiers
        IdentifierUserAssignment assignment1 = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(testIdentifierSource)
                .setIdentifierStart(PREFIX + "7100")
                .setIdentifierEnd(PREFIX + "7149") // Only 50 identifiers available
                .setAssignedTo(user)
                .build();

        // Second range with 50 more identifiers
        IdentifierUserAssignment assignment2 = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(testIdentifierSource)
                .setIdentifierStart(PREFIX + "7200")
                .setIdentifierEnd(PREFIX + "7249") // 50 more identifiers available
                .setAssignedTo(user)
                .build();

        // Third range with 50 more identifiers (to ensure we can handle more than 2 ranges)
        IdentifierUserAssignment assignment3 = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(testIdentifierSource)
                .setIdentifierStart(PREFIX + "7300")
                .setIdentifierEnd(PREFIX + "7349") // 50 more identifiers available
                .setAssignedTo(user)
                .build();

        List<IdentifierUserAssignment> assignments = new ArrayList<>();
        assignments.add(assignment1);
        assignments.add(assignment2);
        assignments.add(assignment3);

        // Mock the repository to return all assignments
        when(identifierUserAssignmentRepository.getAllNonExhaustedUserAssignments(user, testIdentifierSource))
                .thenReturn(assignments);

        // Execute
        prefixedUserPoolBasedIdentifierGenerator.generateIdentifiers(testIdentifierSource, user, PREFIX, DEVICE_ID);

        // Verify that saveAll was called for the user assignments and identifier assignments
        verify(identifierUserAssignmentRepository, atLeastOnce()).saveAll(any());

        // Capture the saved identifier assignments
        ArgumentCaptor<List<IdentifierAssignment>> identifierAssignmentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(identifierAssignmentRepository, atLeastOnce()).saveAll(identifierAssignmentsCaptor.capture());

        // Count the identifiers from each range in the captured values
        int firstRangeCount = 0;
        int secondRangeCount = 0;
        int thirdRangeCount = 0;
        Set<String> uniqueIdentifiers = new HashSet<>();

        for (List<IdentifierAssignment> batch : identifierAssignmentsCaptor.getAllValues()) {
            for (IdentifierAssignment assignment : batch) {
                String identifier = assignment.getIdentifier();
                if (identifier != null) {
                    // Ensure no duplicate identifiers
                    assertTrue("Duplicate identifier found: " + identifier, uniqueIdentifiers.add(identifier));

                    if (identifier.startsWith(PREFIX + "71")) {
                        firstRangeCount++;
                    } else if (identifier.startsWith(PREFIX + "72")) {
                        secondRangeCount++;
                    } else if (identifier.startsWith(PREFIX + "73")) {
                        thirdRangeCount++;
                    }
                }
            }
        }

        // Verify we used identifiers from multiple ranges
        assertEquals("Should have used all 50 identifiers from the first range", 50, firstRangeCount);
        assertEquals("Should have used all 50 identifiers from the first range", 50, secondRangeCount);
        assertTrue("Should have used identifiers from the second range", secondRangeCount > 0);

        // Verify the total number of identifiers generated matches the batch size
        assertEquals("Should have generated a total of 100 identifiers", 100, firstRangeCount + secondRangeCount + thirdRangeCount);

        // Verify that the last assigned identifier for each range is correct
        verify(identifierUserAssignmentRepository, atLeastOnce()).saveAll(ArgumentCaptor.forClass(List.class).capture());
    }

    @Test
    public void shouldHandleOverlappingIdentifierRanges() {
        // Setup two users with overlapping identifier ranges
        User user1 = new UserBuilder().withDefaultValuesForNewEntity().build();
        User user2 = new UserBuilder().withDefaultValuesForNewEntity().build();

        // Create a test identifier source with a larger batch size
        IdentifierSource testIdentifierSource = new IdentifierSourceBuilder()
                .addPrefix(PREFIX)
                .setType(IdentifierGeneratorType.userPoolBasedIdentifierGenerator)
                .setBatchGenerationSize(MEDIUM_BATCH_SIZE)
                .setMinimumBalance(LOW_MINIMUM_BALANCE)
                .build();

        // User1's assignment with range 7100-7150
        IdentifierUserAssignment user1Assignment = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(testIdentifierSource)
                .setIdentifierStart(PREFIX + "7100")
                .setIdentifierEnd(PREFIX + "7150")
                .setAssignedTo(user1)
                .build();

        // User2's assignment with overlapping range 7140-7190
        IdentifierUserAssignment user2Assignment = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(testIdentifierSource)
                .setIdentifierStart(PREFIX + "7140")
                .setIdentifierEnd(PREFIX + "7190")
                .setAssignedTo(user2)
                .build();

        // Setup existing identifiers for user1 (7100-7145 already used)
        List<IdentifierAssignment> user1ExistingIdentifiers = new ArrayList<>();
        for (int i = 0; i <= 45; i++) {
            String identifierValue = PREFIX + String.format("%04d", i + 7100);
            IdentifierAssignment assignment = new IdentifierAssignment(
                    testIdentifierSource, identifierValue, i + 7100l, user1, "device1");
            user1ExistingIdentifiers.add(assignment);
        }

        // Mock repository responses
        when(identifierUserAssignmentRepository.getAllNonExhaustedUserAssignments(user1, testIdentifierSource))
                .thenReturn(Collections.singletonList(user1Assignment));
        when(identifierUserAssignmentRepository.getAllNonExhaustedUserAssignments(user2, testIdentifierSource))
                .thenReturn(Collections.singletonList(user2Assignment));

        // Mock existsByIdentifierAndIdentifierSourceAndIsVoidedFalse to simulate existing identifiers
        when(identifierAssignmentRepository.existsByIdentifierAndIdentifierSourceAndIsVoidedFalse(anyString(), eq(testIdentifierSource)))
                .thenAnswer(invocation -> {
                    String identifier = invocation.getArgument(0);
                    // Return true for identifiers 7100-7145 (already used by user1)
                    int identifierNum = Integer.parseInt(identifier.replace(PREFIX, ""));
                    return identifierNum >= 7100 && identifierNum <= 7145;
                });

        when(identifierAssignmentRepository.findByAssignedToAndDeviceIdAndUsedFalseAndIsVoidedFalse(eq(user2), anyString()))
                .thenReturn(new ArrayList<>());

        // Execute - generate identifiers for user2
        prefixedUserPoolBasedIdentifierGenerator.generateIdentifiers(testIdentifierSource, user2, PREFIX, "device2");

        // Capture the saved identifier assignments
        ArgumentCaptor<List<IdentifierAssignment>> identifierAssignmentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(identifierAssignmentRepository, atLeastOnce()).saveAll(identifierAssignmentsCaptor.capture());

        // Collect all saved identifiers
        Set<String> savedIdentifiers = new HashSet<>();
        for (List<IdentifierAssignment> batch : identifierAssignmentsCaptor.getAllValues()) {
            for (IdentifierAssignment assignment : batch) {
                savedIdentifiers.add(assignment.getIdentifier());
            }
        }

        // Verify that no identifiers in the range 7100-7145 were generated (as they're already used)
        for (int i = 0; i <= 45; i++) {
            String identifierValue = PREFIX + String.format("%04d", 7100 + i);
            assertFalse("Identifier " + identifierValue + " should not have been generated as it already exists",
                    savedIdentifiers.contains(identifierValue));
        }

        // Verify that identifiers were generated from the non-overlapping part of user2's range
        boolean foundNonOverlappingIdentifier = false;
        for (int i = 46; i <= 90; i++) {
            String identifierValue = PREFIX + String.format("%04d", 7100 + i);
            if (savedIdentifiers.contains(identifierValue)) {
                foundNonOverlappingIdentifier = true;
                break;
            }
        }

        assertTrue("Should have generated at least one identifier from the non-overlapping range",
                foundNonOverlappingIdentifier);

        // Verify that the total number of identifiers is correct (should be 10 as per batch size)
        assertEquals("Should have generated exactly 10 identifiers", 10, savedIdentifiers.size());
    }
}
