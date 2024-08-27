package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.*;
import org.avni.server.dao.CatchmentRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
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

    @Test
    public void shouldCreateUpdate() throws IDPException {
        // new catchment, new user
        success(
                header("Location with full hierarchy", "Catchment Name", "Username", "Full Name of User", "Email Address", "Mobile Number", "Preferred Language", "Track Location", "Date picker mode", "Enable Beneficiary mode", "Identifier Prefix", "User Groups", "SubjectTypeWithSyncAttributeBasedSync->Sync Concept"),
                dataRow("Bihar, District1, Block11", "Catchment 1", "username1@example", "User 1", "username1@example.com", "9455509147", "English", "true", "spinner", "false", "", "User Group 1", "Answer 1"),
                catchmentCreated(true),
                userCreated(true));
    }
}
