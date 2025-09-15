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
import org.mockito.Mock;
import org.springframework.test.context.jdbc.Sql;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

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

    @Mock
    private IdentifierUserAssignmentRepository identifierUserAssignmentRepository;

    @Mock
    private IdentifierAssignmentRepository identifierAssignmentRepository;

    private User user;
    private IdentifierSource identifierSource;
    private PrefixedUserPoolBasedIdentifierGenerator prefixedUserPoolBasedIdentifierGenerator;

    @Before
    public void setup() {
        openMocks(this);
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
                .setMinLength(5)
                .setMaxLength(6)
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
    public void shouldAddPaddingToIdentifiersWhenMinLengthIsSpecified() throws Exception {
        // Setup - Create a test method to access the private method
        Method addPaddingMethod = PrefixedUserPoolBasedIdentifierGenerator.class.getDeclaredMethod(
                "addPaddingIfNecessary", String.class, IdentifierSource.class);
        addPaddingMethod.setAccessible(true);

        // Create identifier source with min length requirement
        IdentifierSource identifierSource = new IdentifierSourceBuilder()
                .addPrefix(PREFIX) // "PH"
                .setType(IdentifierGeneratorType.userPoolBasedIdentifierGenerator)
                .setMinLength(7) // Prefix(2) + numeric part(5) = 7
                .setMaxLength(8)
                .build();

        // Test cases with different numeric parts
        String result1 = (String) addPaddingMethod.invoke(prefixedUserPoolBasedIdentifierGenerator, "123", identifierSource);
        String result2 = (String) addPaddingMethod.invoke(prefixedUserPoolBasedIdentifierGenerator, "12345", identifierSource);
        String result3 = (String) addPaddingMethod.invoke(prefixedUserPoolBasedIdentifierGenerator, "123456", identifierSource);

        // Verify padding is added correctly
        assertEquals("Should add padding to short identifiers", "00123", result1);
        assertEquals("Should not add padding when length is exactly right", "12345", result2);
        assertEquals("Should not add padding when length exceeds minimum", "123456", result3);
    }

    @Test
    public void shouldHandleNullPrefixWhenAddingPadding() throws Exception {
        // Setup - Create a test method to access the private method
        Method addPaddingMethod = PrefixedUserPoolBasedIdentifierGenerator.class.getDeclaredMethod(
                "addPaddingIfNecessary", String.class, IdentifierSource.class);
        addPaddingMethod.setAccessible(true);

        // Create identifier source with null prefix
        IdentifierSource identifierSource = new IdentifierSourceBuilder()
                .setType(IdentifierGeneratorType.userPoolBasedIdentifierGenerator)
                .setMinLength(5) // No prefix + numeric part(5) = 5
                .setMaxLength(6)
                .build();

        // Test case with a short numeric part
        String result = (String) addPaddingMethod.invoke(prefixedUserPoolBasedIdentifierGenerator, "123", identifierSource);

        // Verify padding is added correctly
        assertEquals("Should add padding to reach min length with null prefix", "00123", result);
    }

    @Test
    public void shouldVerifyPaddingInGeneratedIdentifiers() {
        // Setup
        IdentifierSource identifierSource = new IdentifierSourceBuilder()
                .addPrefix(PREFIX) // "PH"
                .setType(IdentifierGeneratorType.userPoolBasedIdentifierGenerator)
                .setBatchGenerationSize(5L)
                .setMinimumBalance(2L)
                .setMinLength(7) // Prefix(2) + numeric part(5) = 7
                .setMaxLength(8)
                .build();

        // Create an assignment with small numbers that will need padding
        IdentifierUserAssignment assignment = new IdentifierUserAssignmentBuilder()
                .setIdentifierSource(identifierSource)
                .setIdentifierStart(PREFIX + "1") // This should become PH00001 with padding
                .setIdentifierEnd(PREFIX + "10") // This should become PH00010 with padding
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

        // Capture the saved identifier assignments
        ArgumentCaptor<List<IdentifierAssignment>> identifierAssignmentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(identifierAssignmentRepository, atLeastOnce()).saveAll(identifierAssignmentsCaptor.capture());

        // Verify padding in generated identifiers
        List<IdentifierAssignment> capturedAssignments = identifierAssignmentsCaptor.getValue();
        for (IdentifierAssignment identifierAssignment : capturedAssignments) {
            String identifier = identifierAssignment.getIdentifier();
            assertTrue("Identifier should start with prefix", identifier.startsWith(PREFIX));

            // Remove prefix and check the numeric part
            String numericPart = identifier.substring(PREFIX.length());
            assertEquals("Numeric part should be padded to 5 digits", 5, numericPart.length());
            assertTrue("Numeric part should start with zeros for small numbers", numericPart.startsWith("0"));
        }
    }

}
