package org.avni.server.dao.application;

import org.avni.server.application.Form;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestProgramService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.jdbc.Sql;

import static junit.framework.TestCase.fail;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class FormMappingRepositoryTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private FormMappingRepository formMappingRepository;
    @Autowired
    private FormRepository formRepository;

    @Before
    public void setup() {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        setUser(organisationData.getUser().getUsername());
    }

    private void tryFailedFormMappingSave(FormMapping formMapping) {
        try {
            formMappingRepository.saveFormMapping(formMapping);
            fail("Successfully saved form mapping");
        } catch (JpaSystemException ignored) {
            ignored.printStackTrace();
        }
    }

    @Test
    public void doNotAllowDuplicateFormMappingForSubjectType() {
        FormMapping subjectTypeFormMapping = testSubjectTypeService.createWithDefaultsAndGetFormMapping(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType1")
                        .setName("subjectType1")
                        .build());
        FormMapping incumbentFormMapping = new FormMappingBuilder()
                .withSubjectType(subjectTypeFormMapping.getSubjectType())
                .withForm(subjectTypeFormMapping.getForm()).build();
        tryFailedFormMappingSave(incumbentFormMapping);
    }

    @Test
    public void doNotAllowUpdateOfFormMappingForExistingSubjectType() {
        FormMapping subjectTypeFormMapping1 = testSubjectTypeService.createWithDefaultsAndGetFormMapping(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType1")
                        .setName("subjectType1")
                        .build());
        FormMapping subjectTypeFormMapping2 = testSubjectTypeService.createWithDefaultsAndGetFormMapping(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType2")
                        .setName("subjectType2")
                        .build());
        subjectTypeFormMapping2.setSubjectType(subjectTypeFormMapping1.getSubjectType());
        tryFailedFormMappingSave(subjectTypeFormMapping2);
    }

    @Test
    public void doNotAllowDuplicateFormMappingForSameSubjectTypeWithANewForm() {
        FormMapping subjectTypeFormMapping = testSubjectTypeService.createWithDefaultsAndGetFormMapping(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType1")
                        .setName("subjectType1")
                        .build());
        Form newForm = new TestFormBuilder().withDefaultFieldsForNewEntity().withFormType(FormType.IndividualProfile).build();
        formRepository.save(newForm);
        FormMapping incumbentFormMapping = new FormMappingBuilder()
                .withSubjectType(subjectTypeFormMapping.getSubjectType())
                .withForm(newForm).build();
        tryFailedFormMappingSave(incumbentFormMapping);
    }

    @Test
    public void doNotAllowDuplicateFormMappingForProgramEnrolment() {
        FormMapping subjectTypeFormMapping = testSubjectTypeService.createWithDefaultsAndGetFormMapping(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType1")
                        .setName("subjectType1")
                        .build());
        FormMapping programFormMapping = testProgramService.addProgramAndGetFormMapping(
                new ProgramBuilder()
                        .withName("program1")
                        .build(),
                subjectTypeFormMapping.getSubjectType());
        FormMapping incumbentFormMapping = new FormMappingBuilder()
                .withSubjectType(programFormMapping.getSubjectType())
                .withProgram(programFormMapping.getProgram())
                .withForm(programFormMapping.getForm()).build();
        tryFailedFormMappingSave(incumbentFormMapping);
    }

    @Test
    public void doNotAllowDuplicateFormMappingForProgramEnrolmentExit() {
        FormMapping subjectTypeFormMapping = testSubjectTypeService.createWithDefaultsAndGetFormMapping(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType1")
                        .setName("subjectType1")
                        .build());
        FormMapping programFormMapping = testProgramService.addProgramAndGetFormMapping(
                new ProgramBuilder()
                        .withName("program1")
                        .build(),
                subjectTypeFormMapping.getSubjectType());
        FormMapping programExitMapping = testProgramService.addProgramExitMapping(programFormMapping.getProgram(), programFormMapping.getSubjectType());

        FormMapping incumbentFormMapping = new FormMappingBuilder()
                .withSubjectType(programFormMapping.getSubjectType())
                .withProgram(programFormMapping.getProgram())
                .withForm(programExitMapping.getForm()).build();
        tryFailedFormMappingSave(incumbentFormMapping);
    }
}
