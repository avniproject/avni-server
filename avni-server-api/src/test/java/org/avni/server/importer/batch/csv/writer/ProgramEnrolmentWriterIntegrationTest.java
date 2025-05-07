package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormMapping;
import org.avni.server.application.Subject;
import org.avni.server.config.InvalidConfigurationException;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.importer.batch.csv.writer.header.ProgramEnrolmentHeadersCreator;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestFormService;
import org.avni.server.service.builder.TestLocationService;
import org.joda.time.LocalDate;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class ProgramEnrolmentWriterIntegrationTest extends BaseCSVImportTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private TestLocationService testLocationService;
    @Autowired
    private OperationalSubjectTypeRepository operationalSubjectTypeRepository;
    @Autowired
    private SubjectTypeRepository subjectTypeRepository;
    @Autowired
    private ProgramEnrolmentWriter programEnrolmentWriter;
    @Autowired
    private TestFormService testFormService;
    @Autowired
    private IndividualRepository individualRepository;
    @Autowired
    private ProgramEnrolmentRepository programEnrolmentRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private OperationalProgramRepository operationalProgramRepository;
    @Autowired
    private ProgramEnrolmentHeadersCreator programEnrolmentHeadersCreator;

    private String[] validHeader() {
        return header("Id from previous system",
                "Subject Id from previous system",
                "Program",
                "Enrolment Date",
                "Enrolment Location",
                "\"Single Select Coded\"");
    }

    private String[] validDataRow() {
        return dataRow("EFGH",
                "ABCD",
                "Program1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "SSC Answer 1");
    }

    private String[] validDataRowWithoutLegacyId() {
        return dataRow("",
                "ABCD",
                "Program1",
                "2020-01-01",
                "21.5135243,85.6731848",
                "SSC Answer 1");    }

    @Test
    public void headerWithWrongFields() {
        failure(header("Id from previus system",
                        "Subject Id from previous system",
                        "Program",
                        "Enrolent Date",
                        "Enrolment Location",
                        "\"Single SSSelect Coded\""),
                validDataRow(),
                "mandatory columns are missing in header from uploaded file - enrolment date. please refer to sample file for the list of mandatory headers. unknown headers - enrolent date, single ssselect coded, id from previus system included in file. please refer to sample file for valid list of headers.");
    }

    @Override
    public void setUp() throws Exception {
        testDataSetupService.setupOrganisation("example", "User Group 1");
        AddressLevelType district = new AddressLevelTypeBuilder().name("District").level(3d).withUuid(UUID.randomUUID())
                .build();
        AddressLevelType state = new AddressLevelTypeBuilder().name("State").level(4d).withUuid(UUID.randomUUID())
                .build();
        testDataSetupService.saveLocationTypes(Arrays.asList(district, state));
        List<Concept> singleSelectConcepts = new ArrayList<>();
        Concept singleSelectCoded = testConceptService.createCodedConcept("Single Select Coded", "SSC Answer 1",
                "SSC Answer 2", "SSC Answer 3");
        singleSelectConcepts.add(singleSelectCoded);

        SubjectType subjectType = subjectTypeRepository.save(new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setAllowProfilePicture(true)
                .setType(Subject.Person)
                .setName("SubjectType1").build());
        operationalSubjectTypeRepository
                .save(OperationalSubjectType.fromSubjectType(subjectType, UUID.randomUUID().toString()));
        Individual subject = new SubjectBuilder()
                .withRegistrationDate(LocalDate.now())
                .withSubjectType(subjectType)
                .withFirstName("John")
                .withLegacyId("ABCD")
                .build();
        individualRepository.save(subject);

        Program program = programRepository.save(new ProgramBuilder().withName("Program1").withUuid(UUID.randomUUID().toString()).build());
        operationalProgramRepository.save(OperationalProgram.fromProgram(program));

        FormMapping enrolmentFormMapping = testFormService.createEnrolmentForm(subjectType, program, "Enrolment Form",
                singleSelectConcepts.stream().map(Concept::getName).collect(Collectors.toList()),
                new ArrayList<>());

        AddressLevel bihar = testLocationService
                .save(new AddressLevelBuilder().withDefaultValuesForNewEntity().title("Bihar").type(state).build());
        testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity()
                .title("District1").parent(bihar).type(district).build());

        String[] allHeaders = programEnrolmentHeadersCreator.getAllHeaders(enrolmentFormMapping,null);
        System.out.println(String.join(",", allHeaders));
    }

    @Test
    public void shouldCreateUpdate() throws ValidationException, InvalidConfigurationException {
        // new subject
        String[] header = validHeader();
        String[] dataRow = validDataRow();

        programEnrolmentWriter.write(Chunk.of(new Row(header, dataRow)));
        ProgramEnrolment enrolment = programEnrolmentRepository.findByLegacyId("EFGH");
        assertEquals(1, enrolment.getObservations().size());

        // Second insert should fail with ValidationException
        Exception exception = assertThrows(ValidationException.class, () -> {
            programEnrolmentWriter.write(Chunk.of(new Row(header, dataRow)));
        });
        assertTrue(exception.getMessage().toLowerCase().contains("entry with id from previous system, efgh already present in avni"));
    }

    private void success(String[] headers, String[] values) throws InvalidConfigurationException {
        try {
            long previousCount = programEnrolmentRepository.count();
            programEnrolmentWriter.write(Chunk.of(new Row(headers, values)));
            assertEquals(previousCount + 1, programEnrolmentRepository.count());
        } catch (ValidationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void allowWithoutLegacyId() throws InvalidConfigurationException {
        success(validHeader(), validDataRowWithoutLegacyId());
        success(validHeader(), validDataRowWithoutLegacyId());
    }

    private void failure(String[] headers, String[] cells, String errorMessage) {
        long before = individualRepository.count();
        try {
            programEnrolmentWriter.write(Chunk.of(new Row(headers, cells)));
            fail();
        } catch (Exception e) {
            Assert.assertEquals(errorMessage.toLowerCase(), e.getMessage().toLowerCase());
        }
        long after = individualRepository.count();
        Assert.assertEquals(before, after);
    }
}
