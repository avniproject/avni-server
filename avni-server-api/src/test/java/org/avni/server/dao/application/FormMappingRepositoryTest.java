package org.avni.server.dao.application;

import org.avni.server.application.Form;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.EncounterTypeBuilder;
import org.avni.server.domain.Program;
import org.avni.server.domain.SubjectType;
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

import java.util.List;
import java.util.UUID;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;
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
    @Autowired
    private EncounterTypeRepository encounterTypeRepository;

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
//            ignored.printStackTrace();
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
        assertNotNull(formMappingRepository.getRequiredFormMapping("subjectType1", null, null, FormType.IndividualProfile));
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
        assertNotNull(formMappingRepository.getRequiredFormMapping("subjectType1", null, null, FormType.IndividualProfile));
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
                        .withUuid("program1")
                        .build(),
                subjectTypeFormMapping.getSubjectType());

        assertNotNull(formMappingRepository.getRequiredFormMapping("subjectType1", "program1", null, FormType.ProgramEnrolment));

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
                        .withUuid("program1")
                        .build(),
                subjectTypeFormMapping.getSubjectType());
        FormMapping programExitMapping = testProgramService.addProgramExitMapping(programFormMapping.getProgram(), programFormMapping.getSubjectType());

        FormMapping requiredFormMapping = formMappingRepository.getRequiredFormMapping("subjectType1", "program1", null, FormType.ProgramEnrolment);
        assertNotNull(requiredFormMapping);

        FormMapping incumbentFormMapping = new FormMappingBuilder()
                .withSubjectType(programFormMapping.getSubjectType())
                .withProgram(programFormMapping.getProgram())
                .withForm(programExitMapping.getForm()).build();
        tryFailedFormMappingSave(incumbentFormMapping);
    }

    @Test
    public void getRequiredFormMappingShouldGetFormMappingBasedOnExactMatch() {
        FormMapping subjectTypeFormMapping = testSubjectTypeService.createWithDefaultsAndGetFormMapping(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType1")
                        .setName("subjectType1")
                        .build());
        FormMapping programFormMapping = testProgramService.addProgramAndGetFormMapping(
                new ProgramBuilder()
                        .withName("program1")
                        .withUuid("program1")
                        .build(),
                subjectTypeFormMapping.getSubjectType());

        programFormMapping.setVoided(true);
        formMappingRepository.save(programFormMapping);

        FormMapping requiredFormMapping = formMappingRepository.getRequiredFormMapping("subjectType1", null, null, FormType.IndividualProfile);
        assertNotNull(requiredFormMapping);
    }

    @Test(expected = JpaSystemException.class)
    public void doNotAllowWrongFormType() {
        FormMapping subjectTypeFormMapping = testSubjectTypeService.createWithDefaultsAndGetFormMapping(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType1")
                        .setName("subjectType1")
                        .build());
        FormMapping programFormMapping = testProgramService.addProgramAndGetFormMapping(
                new ProgramBuilder()
                        .withName("program1")
                        .withUuid("program1")
                        .build(),
                subjectTypeFormMapping.getSubjectType());

        EncounterType encounterType = new EncounterTypeBuilder().withName("encounterTyp1").withUuid(UUID.randomUUID().toString()).build();
        encounterTypeRepository.save(encounterType);
        Form form = new TestFormBuilder().withDefaultFieldsForNewEntity().withFormType(FormType.Encounter).build();
        formRepository.save(form);
        formMappingRepository.saveFormMapping(new FormMappingBuilder().withForm(form).withEncounterType(encounterType).withProgram(programFormMapping.getProgram()).withSubjectType(programFormMapping.getSubjectType()).build());
    }

    // Approval / Rejection form mappings (#1050).
    //
    // The form-type-consistency block in check_form_mapping_uniqueness is an exclusion list: a match
    // raises the exception. Approval and Rejection have no branch there, so every shape is permitted.
    // These tests pin that down, and pin the property the coded-approval design rests on - that two
    // different form types may share one combination while the same type may not be repeated.
    // All fixtures are non-voided, because the function returns early on formMappingIsVoided = true.

    private SubjectType approvalTestSubjectType() {
        return testSubjectTypeService.createWithDefaultsAndGetFormMapping(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType1")
                        .setName("subjectType1")
                        .build()).getSubjectType();
    }

    private Program approvalTestProgram(SubjectType subjectType) {
        return testProgramService.addProgramAndGetFormMapping(
                new ProgramBuilder().withName("program1").withUuid("program1").build(),
                subjectType).getProgram();
    }

    private EncounterType approvalTestEncounterType() {
        EncounterType encounterType = new EncounterTypeBuilder()
                .withName("encounterType1").withUuid(UUID.randomUUID().toString()).build();
        return encounterTypeRepository.save(encounterType);
    }

    private Form saveFormOfType(FormType formType) {
        return formRepository.save(new TestFormBuilder().withDefaultFieldsForNewEntity().withFormType(formType).build());
    }

    private void assertSavesInAllFourShapes(FormType formType) {
        SubjectType subjectType = approvalTestSubjectType();
        Program program = approvalTestProgram(subjectType);
        EncounterType encounterType = approvalTestEncounterType();
        Form form = saveFormOfType(formType);

        assertNotNull("subject type only", formMappingRepository.saveFormMapping(
                new FormMappingBuilder().withForm(form).withSubjectType(subjectType).build()));
        assertNotNull("with programme", formMappingRepository.saveFormMapping(
                new FormMappingBuilder().withForm(form).withSubjectType(subjectType).withProgram(program).build()));
        assertNotNull("with visit type", formMappingRepository.saveFormMapping(
                new FormMappingBuilder().withForm(form).withSubjectType(subjectType).withEncounterType(encounterType).build()));
        assertNotNull("with programme and visit type", formMappingRepository.saveFormMapping(
                new FormMappingBuilder().withForm(form).withSubjectType(subjectType)
                        .withProgram(program).withEncounterType(encounterType).build()));
    }

    @Test
    public void allowApprovalFormMappingInAllFourShapes() {
        assertSavesInAllFourShapes(FormType.Approval);
    }

    /**
     * Registration lookup must survive a subject-type-only Approval mapping. getRegistrationFormMapping
     * returns a single entity, so without a form type filter a second mapping with no programme and no
     * encounter type makes Spring Data throw IncorrectResultSizeDataAccessException - breaking CSV
     * subject import (SubjectWriter), the subject registration reporting view and the Subject Type
     * admin screens, at runtime rather than at build.
     */
    @Test
    public void registrationFormMappingIsFoundEvenWhenAnApprovalFormSharesTheSubjectTypeOnlyShape() {
        SubjectType subjectType = approvalTestSubjectType();
        formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(saveFormOfType(FormType.Approval)).withSubjectType(subjectType).build());
        formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(saveFormOfType(FormType.Rejection)).withSubjectType(subjectType).build());

        FormMapping registrationFormMapping = formMappingRepository.getRegistrationFormMapping(subjectType);

        assertNotNull(registrationFormMapping);
        assertEquals(FormType.IndividualProfile, registrationFormMapping.getForm().getFormType());
    }

    @Test
    public void allowRejectionFormMappingInAllFourShapes() {
        assertSavesInAllFourShapes(FormType.Rejection);
    }

    @Test
    public void doNotAllowTheSameApprovalFormTypeTwiceOnOneCombination() {
        SubjectType subjectType = approvalTestSubjectType();
        formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(saveFormOfType(FormType.Approval)).withSubjectType(subjectType).build());

        tryFailedFormMappingSave(new FormMappingBuilder()
                .withForm(saveFormOfType(FormType.Approval)).withSubjectType(subjectType).build());
    }

    /**
     * Two different approval forms, one per programme, on one subject type. The programme is part of
     * the uniqueness key, so both save. This is the shape that breaks if a caller ever routes an
     * Approval mapping through FormMappingService.saveFormMapping(FormMappingParameterObject, ..):
     * setProgramIfRequired guards on FormType.isLinkedToProgram(), which is false for Approval and
     * Rejection because their programme is optional, so the programme would be dropped and both
     * mappings would collapse onto the same key. Not reachable today - App Designer and bundle
     * import both go through createOrUpdateFormMapping, which sets the programme directly.
     */
    @Test
    public void allowTwoApprovalFormsOnDifferentProgrammesOfOneSubjectType() {
        SubjectType subjectType = approvalTestSubjectType();
        Program programOne = approvalTestProgram(subjectType);
        Program programTwo = testProgramService.addProgramAndGetFormMapping(
                new ProgramBuilder().withName("program2").withUuid("program2").build(), subjectType).getProgram();

        assertNotNull(formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(saveFormOfType(FormType.Approval)).withSubjectType(subjectType).withProgram(programOne).build()));
        assertNotNull(formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(saveFormOfType(FormType.Approval)).withSubjectType(subjectType).withProgram(programTwo).build()));
    }

    /**
     * Voiding an approval mapping must free the combination for a different approval form. The
     * duplicate check compares against sibling rows; without a voided filter on those siblings it
     * matches the row the administrator just removed and refuses the replacement with "Duplicate
     * form mapping exists". Registration and enrolment forms never reach this because
     * FormMappingService.saveFormMapping reuses the existing row, but Approval and Rejection go
     * through createOrUpdateFormMapping, which keys on the request UUID and so creates a new row.
     */
    @Test
    public void allowANewApprovalFormOnceTheOldMappingIsVoided() {
        SubjectType subjectType = approvalTestSubjectType();
        FormMapping original = formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(saveFormOfType(FormType.Approval)).withSubjectType(subjectType).build());
        original.setVoided(true);
        formMappingRepository.saveFormMapping(original);

        assertNotNull(formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(saveFormOfType(FormType.Approval)).withSubjectType(subjectType).build()));
    }

    // Sibling lookup for #1052. The tuple is (subject type, programme, visit type); organisation is left
    // to row level security, as with every other query in this repository.
    //
    // Approval is switched on by updating the mapping that already exists for a combination, not by adding
    // a second one - createWithDefaultsAndGetFormMapping and addProgramAndGetFormMapping already create the
    // registration and enrolment mappings, and a second mapping of the same form type on one combination is
    // refused by check_form_mapping_uniqueness. That is also what an administrator actually does.

    private FormMapping switchApprovalOnFor(FormMapping formMapping) {
        formMapping.setEnableApproval(true);
        return formMappingRepository.saveFormMapping(formMapping);
    }

    /**
     * The subject-type-only shape is the one an implicit join would silently lose (see the left joins in
     * findApprovalEnabledMappingsForCombination). It is also the commonest shape in production, so losing
     * it would refuse almost every legitimate approval form while looking like it worked.
     */
    @Test
    public void findsApprovalEnabledMappingsWhereProgrammeAndVisitTypeAreNull() {
        SubjectType subjectType = approvalTestSubjectType();
        switchApprovalOnFor(formMappingRepository.getRegistrationFormMapping(subjectType));

        List<FormMapping> found = formMappingRepository.findApprovalEnabledMappingsForCombination(
                subjectType.getId(), null, null);

        assertEquals(1, found.size());
        assertEquals(FormType.IndividualProfile, found.get(0).getForm().getFormType());
    }

    @Test
    public void findsApprovalEnabledMappingsOnTheProgrammeAndVisitTypeCombination() {
        SubjectType subjectType = approvalTestSubjectType();
        Program program = approvalTestProgram(subjectType);
        EncounterType encounterType = approvalTestEncounterType();
        formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(saveFormOfType(FormType.ProgramEncounter)).withSubjectType(subjectType)
                .withProgram(program).withEncounterType(encounterType).withEnableApproval(true).build());

        assertEquals(1, formMappingRepository.findApprovalEnabledMappingsForCombination(
                subjectType.getId(), program.getId(), encounterType.getId()).size());
    }

    /**
     * A mapping on a different combination must not count as a sibling. Without exclusive null branches a
     * programme-scoped mapping would answer a subject-type-only query.
     */
    @Test
    public void doesNotFindApprovalEnabledMappingsFromADifferentCombination() {
        SubjectType subjectType = approvalTestSubjectType();
        Program program = approvalTestProgram(subjectType);
        switchApprovalOnFor(formMappingRepository.getProgramEnrolmentFormMapping(subjectType, program));

        assertEquals(0, formMappingRepository.findApprovalEnabledMappingsForCombination(
                subjectType.getId(), null, null).size());
    }

    /**
     * The registration mapping created by the fixture has approval switched off, and the voided mapping
     * has it switched on. Neither may be returned. A voided duplicate of an existing form type is
     * accepted by the constraint, which returns early on voided rows - which is what makes this fixture
     * possible at all.
     */
    @Test
    public void doesNotFindMappingsWithApprovalSwitchedOffOrVoided() {
        SubjectType subjectType = approvalTestSubjectType();
        FormMapping voided = new FormMappingBuilder()
                .withForm(saveFormOfType(FormType.IndividualProfile)).withSubjectType(subjectType)
                .withEnableApproval(true).build();
        voided.setVoided(true);
        formMappingRepository.saveFormMapping(voided);

        assertEquals(0, formMappingRepository.findApprovalEnabledMappingsForCombination(
                subjectType.getId(), null, null).size());
    }

    /**
     * 108 production triples carry two approval-enabled form types, so the caller must handle a list
     * rather than assume a single sibling.
     */
    @Test
    public void findsEveryApprovalEnabledMappingOnOneCombination() {
        SubjectType subjectType = approvalTestSubjectType();
        Program program = approvalTestProgram(subjectType);
        switchApprovalOnFor(formMappingRepository.getProgramEnrolmentFormMapping(subjectType, program));
        formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(saveFormOfType(FormType.ProgramExit)).withSubjectType(subjectType)
                .withProgram(program).withEnableApproval(true).build());

        assertEquals(2, formMappingRepository.findApprovalEnabledMappingsForCombination(
                subjectType.getId(), program.getId(), null).size());
    }

    @Test
    public void allowApprovalAndRejectionOnTheSameCombination() {
        SubjectType subjectType = approvalTestSubjectType();
        Program program = approvalTestProgram(subjectType);

        assertNotNull(formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(saveFormOfType(FormType.Approval)).withSubjectType(subjectType).withProgram(program).build()));
        assertNotNull(formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(saveFormOfType(FormType.Rejection)).withSubjectType(subjectType).withProgram(program).build()));
    }
}
