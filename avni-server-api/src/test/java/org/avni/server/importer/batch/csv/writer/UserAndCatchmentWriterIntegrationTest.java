package org.avni.server.importer.batch.csv.writer;

import org.avni.server.dao.CatchmentRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.IDPException;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestLocationService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class UserAndCatchmentWriterIntegrationTest extends BaseCSVImportTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private FormRepository formRepository;
    @Autowired
    private TestLocationService testLocationService;
    @Autowired
    private UserAndCatchmentWriter userAndCatchmentWriter;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CatchmentRepository catchmentRepository;

    @Override
    public void setUp() {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation("example", "User Group 1");
        AddressLevelType block = new AddressLevelTypeBuilder().name("Block").level(2d).withUuid(UUID.randomUUID()).build();
        AddressLevelType district = new AddressLevelTypeBuilder().name("District").level(3d).withUuid(UUID.randomUUID()).build();
        AddressLevelType state = new AddressLevelTypeBuilder().name("State").level(4d).withUuid(UUID.randomUUID()).build();
        testDataSetupService.saveLocationTypes(Arrays.asList(block, district, state));
        Concept codedConcept = testConceptService.createCodedConcept("Sync Concept", "Answer 1", "Answer 2");
        Concept textConcept = testConceptService.createConcept("Text Concept", ConceptDataType.Text);

        testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid(UUID.randomUUID().toString())
                        .setName("SubjectTypeWithSyncAttributeBasedSync")
                        .setSyncRegistrationConcept1Usable(true)
                        .setSyncRegistrationConcept1(codedConcept.getUuid()).build());

        AddressLevel bihar = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Bihar").type(state).build());
        AddressLevel district1 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("District1").parent(bihar).type(district).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block11").parent(district1).type(block).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block12").parent(district1).type(block).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block13").parent(district1).type(block).build());

        AddressLevel district2 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("District2").parent(bihar).type(district).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block21").parent(district2).type(block).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block22").parent(district2).type(block).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Block23").parent(district2).type(block).build());
    }

    private boolean catchmentCreated(boolean b) {
        return b;
    }

    private boolean userCreated(boolean b) {
        return b;
    }

    private void success(String[] headers, String[] cells, boolean catchmentCreated, boolean userCreated) throws IDPException {
        long numberOfUsers = userRepository.count();
        long numberOfCatchments = catchmentRepository.count();
        userAndCatchmentWriter.write(Collections.singletonList(new Row(headers, cells)));
        if (catchmentCreated)
            assertEquals(catchmentRepository.count(), numberOfCatchments + 1);
        else
            assertEquals(catchmentRepository.count(), numberOfCatchments);
        if (userCreated)
            assertEquals(userRepository.count(), numberOfUsers + 1);
        else
            assertEquals(userRepository.count(), numberOfUsers);
    }

    private void failure(String[] headers, String[] cells, String... errorMessages) throws IDPException {
        try {
            userAndCatchmentWriter.write(Collections.singletonList(new Row(headers, cells)));
            fail();
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (message == null) {
                e.printStackTrace();
                fail();
            } else {
                Arrays.stream(errorMessages).forEach(s -> {
                    if (!message.contains(s)) {
                        e.printStackTrace();
                        fail("Expected error message: " + s + " not present in: " + message);
                    }
                });
            }
        }
    }

    private void failsOnMissingHeader(String[] headers, String... errorMessages) throws IDPException {
        try {
            userAndCatchmentWriter.write(Collections.singletonList(new Row(headers, new String[]{})));
            fail();
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (message == null) {
                e.printStackTrace();
                fail();
            } else {
                Arrays.stream(errorMessages).forEach(s -> {
                    if (!message.contains(s)) {
                        e.printStackTrace();
                        fail("Expected error message: " + s + " not present in: " + message);
                    }
                });
            }
        }
    }

    private void treatAsDescriptor(String[] headers, String... additionalHeaders) throws IDPException {
        success(headers, additionalHeaders, false, false);
    }

    @Test
    public void shouldCreateUpdate() throws IDPException {
        // new catchment, new user
        success(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 1", "username1@example", "User 1", "username1@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1"),
                catchmentCreated(true),
                userCreated(true));
        // existing catchment new user
        success(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 1", "username2@example", "User 2", "username2@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1"),
                catchmentCreated(false),
                userCreated(true));
        // new catchment existing user
        success(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 3", "username2@example", "User 2", "username2@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1"),
                catchmentCreated(true),
                userCreated(false));
        // existing catchment existing user
        success(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 3", "username2@example", "User 2", "username2@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1"),
                catchmentCreated(false),
                userCreated(false));
        // with spaces
        success(
                header(" Location with full hierarchy", " Catchment Name", "Username ", " Full Name of User", "Email Address", "Mobile Number", " Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", " Identifier Prefix", " User Groups", " SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow(" Bihar, District1, Block11", " Catchment 4", " username3@example", " User 3", " username3@example.com ", " 9455509147 ", "English ", "true ", " spinner", " false", "", " User Group 1", " Answer 1"),
                catchmentCreated(true),
                userCreated(true));

        // wrong - username, email, phone number, language, track location, date picker mode, enable beneficiary mode
        failure(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 1", "username1@exmplee", "User 1", "username1@examplecom", "9455047", "Irish", "truee", "spinnerr", "falsee", "", "User Group 1", "Answer 1"),
                hasError("Invalid username 'username1@exmplee'. Include correct userSuffix @example at the end"),
                hasError("Invalid email address username1@examplecom"),
                hasError("Provided value 'Mobile Number' for phone number is invalid."),
                hasError("Provided value 'Irish' for Preferred Language is invalid."),
                hasError("Provided value 'spinnerr' for Date picker mode is invalid."),
                hasError("Invalid or Empty value specified for mandatory field Track Location"),
                hasError("Invalid or Empty value specified for mandatory field Enable Beneficiary")
        );
        // empty - catchment name, username, Full Name of User, email, phone number, track location, date picker mode, enable beneficiary mode
        failure(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", " ", "", " ", " ", " ", " ", " ", "", "", "", "User Group 1", "Answer 1"),
                hasError("Invalid or Empty value specified for mandatory field Catchment Name."),
                hasError("Invalid or Empty value specified for mandatory field Username."),
                hasError("Invalid or Empty value specified for mandatory field Full Name of User."),
                hasError("Invalid or Empty value specified for mandatory field Email Address."),
                hasError("Invalid or Empty value specified for mandatory field Mobile Number."),
                hasError("Provided value '' for Date picker mode is invalid."),
                hasError("Invalid or Empty value specified for mandatory field Track Location."),
                hasError("Invalid or Empty value specified for mandatory field Enable Beneficiary")
        );

        // invalid User Group Name
        failure(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 3", "username2@example", "User 2", "username2@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1345", "Answer 1"),
                hasError("Group 'User Group 1345' not found")
        );
        // same user group twice
        success(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 3", "username4@example", "User 4", "username4@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1,User Group 1", "Answer 1"),
                catchmentCreated(false),
                userCreated(true)
        );

        // invalid sync attribute
        failure(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 3", "username2@example", "User 2", "username2@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1223"),
                hasError("'Answer 1223' is not a valid value for the concept 'Sync Concept'.")
        );

        // Wrong location hierarchy
        failure(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, NoBlock11", "Catchment 3", "username2@example", "User 2", "username2@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1"),
                hasError("Provided Location does not exist in Avni. Please add it or check for spelling mistakes 'Bihar, District1, NoBlock11'.")
        );

        // Missing headers - sync attributes
        failsOnMissingHeader(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups"),
                hasError("Mandatory columns are missing from uploaded file - SubjectTypeWithSyncAttributeBasedSync->Sync Concept. Please refer to sample file for the list of mandatory headers.")
        );
        // Missing headers - all
        failsOnMissingHeader(
                header(),
                hasError("Mandatory columns are missing from uploaded file - Track Location, Identifier Prefix, Catchment Name, Full Name of User, Mobile Number, Enable Beneficiary mode, User Groups, Username, SubjectTypeWithSyncAttributeBasedSync->Sync Concept, Preferred Language, Location with full hierarchy, Date picker mode, Email Address. Please refer to sample file for the list of mandatory headers.")
        );

        // allow additional cells in data row
        success(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 3", "username5@example", "User 5", "username5@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1", "Foo"),
                catchmentCreated(false),
                userCreated(true)
        );

        treatAsDescriptor(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Mandatory field. Can be found from Admin -> Locations -> Click Export", "Mandatory field", "Mandatory field", "Mandatory field", "Mandatory field", "Mandatory field. Prefix the mobile number with country code", "Allowed values: {English, Hindi}. Only single value allowed. Default: English", "Allowed values: yes, no. Default: no", "Allowed values: calendar, spinner. Default: calendar", "Allowed values: yes, no. Default: no", "", "Allowed values: {Administrators, Everyone}"));
    }
}
