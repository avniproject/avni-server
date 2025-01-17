package org.avni.server.importer.batch.csv.writer;

import org.avni.server.dao.CatchmentRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.*;
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
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class UserAndCatchmentWriterIntegrationTest extends BaseCSVImportTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestConceptService testConceptService;
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
        testDataSetupService.setupOrganisation("example", "User Group 1");
        AddressLevelType block = new AddressLevelTypeBuilder().name("Block").level(2d).withUuid(UUID.randomUUID()).build();
        AddressLevelType district = new AddressLevelTypeBuilder().name("District").level(3d).withUuid(UUID.randomUUID()).build();
        AddressLevelType state = new AddressLevelTypeBuilder().name("State").level(4d).withUuid(UUID.randomUUID()).build();
        testDataSetupService.saveLocationTypes(Arrays.asList(block, district, state));
        Concept codedConcept = testConceptService.createCodedConcept("Sync Concept", "Answer 1", "Answer 2");
        testConceptService.createConcept("Text Concept", ConceptDataType.Text);

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

    private boolean userCreatedDetails(boolean b) {
        return b;
    }

    private boolean userCreatedDetails(String userName, String datePickerMode, String language, boolean trackLocation, boolean enableBeneficiaryMode, String userGroup) {
        User user = userRepository.findByUsername(userName);
        UserSettings userSettings = user.getUserSettings();
        assertEquals(userSettings.getDatePickerMode(), datePickerMode);
        assertEquals(userSettings.getLocale(), language);
        assertEquals(userSettings.isTrackLocation(), trackLocation);
        assertEquals(userSettings.isEnableBeneficiaryMode(), enableBeneficiaryMode);
        assertTrue(user.isPartOfUserGroup(userGroup));
        return true;
    }

    private String[] has(String... errors) {
        return errors;
    }

    private void success(String[] headers, String[] cells, boolean catchmentCreated, boolean userCreated) throws IDPException {
        long numberOfUsers = userRepository.count();
        long numberOfCatchments = catchmentRepository.count();
        userAndCatchmentWriter.write(Chunk.of(new Row(headers, cells)));
        if (catchmentCreated)
            assertEquals(catchmentRepository.count(), numberOfCatchments + 1);
        else
            assertEquals(catchmentRepository.count(), numberOfCatchments);
        if (userCreated)
            assertEquals(userRepository.count(), numberOfUsers + 1);
        else
            assertEquals(userRepository.count(), numberOfUsers);
    }

    private void failure(String[] headers, String[] cells, String[] errorMessages) throws IDPException {
        try {
            userAndCatchmentWriter.write(Chunk.of(new Row(headers, cells)));
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

    private void failsOnMissingHeader(String[] headers, String[] errorMessages, String... nonExistentErrorMessages) throws IDPException {
        try {
            userAndCatchmentWriter.write(Chunk.of(new Row(headers, new String[]{})));
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
                Arrays.stream(nonExistentErrorMessages).forEach(s -> {
                    if (message.contains(s)) {
                        e.printStackTrace();
                        fail("Unexpected error message: " + s + " present in: " + message);
                    }
                });
            }
        }
    }

    private void treatAsDescriptor(String[] headers, String... additionalHeaders) throws IDPException {
        success(headers, additionalHeaders, false, false);
    }

    private String language(String language) {
        return language;
    }

    private boolean trackLocation(boolean b) {
        return b;
    }

    private boolean enableBeneficiaryMode(boolean b) {
        return b;
    }

    private String userGroup(String userGroup) {
        return userGroup;
    }

    private String datePickerMode(String spinner) {
        return spinner;
    }

    private String user(String user) {
        return user;
    }

    @Test
    public void shouldCreateUpdate() throws IDPException {
        // new catchment, new user
        success(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 1", "username1@example", "User 1", "username1@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1"),
                catchmentCreated(true),
                userCreatedDetails(true));
        // existing catchment new user
        success(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 1", "username2@example", "User 2", "username2@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1"),
                catchmentCreated(false),
                userCreatedDetails(true));
        // new catchment existing user
        success(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 3", "username2@example", "User 2", "username2@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1"),
                catchmentCreated(true),
                userCreatedDetails(false));
        // existing catchment existing user
        success(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 3", "username2@example", "User 2", "username2@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1"),
                catchmentCreated(false),
                userCreatedDetails(false));
        // with spaces
        success(
                header(" Location with full hierarchy", " Catchment Name", "Username ", " Full Name of User", "Email Address", "Mobile Number", " Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", " Identifier Prefix", " User Groups", " SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow(" Bihar, District1, Block11", " Catchment 4", " username3@example", " User 3", " username3@example.com ", " 9455509147 ", "English ", "true ", " spinner", " false", "", " User Group 1", " Answer 1"),
                catchmentCreated(true),
                userCreatedDetails(true));
        // without mandatory fields
        success(
                header(" Location with full hierarchy", " Catchment Name", "Username ", " Full Name of User", "Email Address", "Mobile Number", " Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", " Identifier Prefix", " User Groups", " SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow(" Bihar, District1, Block11", " Catchment 5", " username6@example", " User 6", " username6@example.com ", " 9455509147 ", "English ", "", " spinner", " ", "", " User Group 1", " Answer 1"),
                catchmentCreated(true),
                userCreatedDetails(true));
        // without mandatory fields and including in header
        success(
                header(" Location with full hierarchy", " Catchment Name", "Username ", " Full Name of User", "Email Address", "Mobile Number", " Preferred Language", "Date picker mode", " Identifier Prefix", " User Groups", " SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow(" Bihar, District1, Block11", " Catchment 6", " username7@example", " User 7", " username6@example.com ", " 9455509147 ", "English ", " spinner", "", " User Group 1", " Answer 1"),
                catchmentCreated(true),
                userCreatedDetails(true));
        // without mandatory fields and including in header including ones with default value
        success(
                header(" Location with full hierarchy", " Catchment Name", "Username ", " Full Name of User", "Email Address", "Mobile Number", " SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow(" Bihar, District1, Block11", " Catchment 6", " username8@example", " User 8", " username8@example.com ", " 9455509147 ", "Answer 1"),
                catchmentCreated(false),
                userCreatedDetails(true));
        userCreatedDetails(user("username8@example"), datePickerMode("calendar"), language("en"), trackLocation(false), enableBeneficiaryMode(false), userGroup("Everyone"));

        // wrong - username, email, phone number, language, track location, date picker mode, enable beneficiary mode
        failure(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 1", "username1@exmplee", "User 1", "username1@examplecom", "9455047", "Irish", "truee", "spinnerr", "falsee", "", "User Group 1", "Answer 1"),
                has(
                        error("Invalid username 'username1@exmplee'. Include correct userSuffix @example at the end"),
                        error("Invalid email address username1@examplecom"),
                        error("Provided value 'Mobile Number' for phone number is invalid."),
                        error("Provided value 'Irish' for Preferred Language is invalid."),
                        error("Provided value 'spinnerr' for Date picker mode is invalid."),
                        error("Provided value 'truee' for track location is invalid."),
                        error("Provided value 'falsee' for enable beneficiary mode is invalid.")
                )
        );
        // empty - catchment name, username, Full Name of User, email, phone number, track location, date picker mode, enable beneficiary mode
        failure(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", " ", "", " ", " ", " ", " ", " ", "", "", "", "User Group 1", "Answer 1"),
                has(
                        error("Invalid or Empty value specified for mandatory field Catchment Name."),
                        error("Invalid or Empty value specified for mandatory field Username."),
                        error("Invalid or Empty value specified for mandatory field Full Name of User."),
                        error("Invalid or Empty value specified for mandatory field Email Address."),
                        error("Invalid or Empty value specified for mandatory field Mobile Number.")
                )
        );

        // invalid User Group Name
        failure(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 3", "username2@example", "User 2", "username2@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1345", "Answer 1"),
                has(error("Group 'User Group 1345' not found"))
        );
        // same user group twice
        success(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 3", "username4@example", "User 4", "username4@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1,User Group 1", "Answer 1"),
                catchmentCreated(false),
                userCreatedDetails(true)
        );

        // invalid sync attribute
        failure(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 3", "username2@example", "User 2", "username2@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1223"),
                has(error("'Answer 1223' is not a valid value for the concept 'Sync Concept'."))
        );

        // Wrong location hierarchy
        failure(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, NoBlock11", "Catchment 3", "username2@example", "User 2", "username2@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1"),
                has(error("Provided Location does not exist in Avni. Please add it or check for spelling mistakes and ensure space between two locations 'Bihar, District1, NoBlock11'"))
        );

        // Missing headers - sync attributes
        failsOnMissingHeader(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups"),
                has(error("Mandatory columns are missing from uploaded file - SubjectTypeWithSyncAttributeBasedSync->Sync Concept. Please refer to sample file for the list of mandatory headers."))
        );
        // Missing headers - all
        failsOnMissingHeader(
                header(),
                has(error("Mandatory columns are missing from uploaded file - Mobile Number, Username, SubjectTypeWithSyncAttributeBasedSync->Sync Concept, Catchment Name, Location with full hierarchy, Full Name of User, Email Address.")),
                doesntHaveError("\"Mandatory columns are missing from uploaded file - Identifier Prefix, Mobile Number, User Groups, Username, SubjectTypeWithSyncAttributeBasedSync->Sync Concept, Preferred Language, Catchment Name, Location with full hierarchy, Date picker mode, Full Name of User, Email Address. Please refer to sample file for the list of mandatory headers.")
        );

        // allow additional cells in data row
        success(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 3", "username5@example", "User 5", "username5@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1", "Foo"),
                catchmentCreated(false),
                userCreatedDetails(true)
        );

        // case in-sensitive columns for date picker mode, preferred language, track location, enable beneficiary mode, user groups
        success(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 3", "username9@example", "User 9", "username9@example.com", "9455509147", "ENglish", "TruE", "SPinner", "FalsE", "", "User GROUP 1", "Answer 1"),
                catchmentCreated(false),
                userCreatedDetails(true)
        );
        userCreatedDetails(user("username9@example"), datePickerMode("spinner"), language("en"), trackLocation(true), enableBeneficiaryMode(false), userGroup("User Group 1"));

        treatAsDescriptor(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Mandatory field. Can be found from Admin -> Locations -> Click Export", "Mandatory field", "Mandatory field", "Mandatory field", "Mandatory field", "Mandatory field. Prefix the mobile number with country code", "Allowed values: {English, Hindi}. Only single value allowed. Default: English", "Allowed values: yes, no. Default: no", "Allowed values: calendar, spinner. Default: calendar", "Allowed values: yes, no. Default: no", "", "Allowed values: {Administrators, Everyone}"));
    }
}
