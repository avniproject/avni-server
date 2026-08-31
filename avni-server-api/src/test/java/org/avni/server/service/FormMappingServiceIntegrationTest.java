package org.avni.server.service;

import org.avni.server.application.Form;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.EncounterTypeBuilder;
import org.avni.server.domain.Program;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.factory.access.TestGroupPrivilegeBuilder;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestGroupService;
import org.avni.server.service.builder.TestProgramService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.web.request.FormMappingContract;
import org.avni.server.web.validation.ValidationException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * #1052 - an Approval or Rejection form may only be attached where an approval can actually happen.
 * App Designer (FormController, FormMappingController) and bundle import (BundleZipFileImporter) both
 * reach this through createOrUpdateFormMapping, so guarding there covers both routes.
 * <p>
 * Approval is switched on by updating the mapping a combination already has, never by adding a second
 * one of the same form type - that is what an administrator does, and a duplicate would be refused by
 * check_form_mapping_uniqueness anyway.
 */
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class FormMappingServiceIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private FormMappingService formMappingService;
    @Autowired
    private FormMappingRepository formMappingRepository;
    @Autowired
    private FormRepository formRepository;
    @Autowired
    private EncounterTypeRepository encounterTypeRepository;

    private SubjectType subjectType;

    @Before
    public void setup() {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        Map<GroupPrivilege, PrivilegeType> privileges = new HashMap<>();
        privileges.put(new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().build(), PrivilegeType.EditApproval);
        privileges.put(new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().build(), PrivilegeType.EditRejection);
        privileges.put(new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().build(), PrivilegeType.EditSubjectType);
        privileges.put(new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().build(), PrivilegeType.EditProgram);
        privileges.put(new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().build(), PrivilegeType.EditEncounterType);
        testGroupService.updateGroup(organisationData.getGroup(), privileges);
        setUser(organisationData.getUser().getUsername());
        subjectType = testSubjectTypeService.createWithDefaultsAndGetFormMapping(
                new SubjectTypeBuilder().setMandatoryFieldsForNewEntity()
                        .setUuid("st1052").setName("st1052").build()).getSubjectType();
    }

    private Form saveFormOfType(FormType formType) {
        return formRepository.save(new TestFormBuilder().withDefaultFieldsForNewEntity().withFormType(formType).build());
    }

    private Program aProgram(String name) {
        return testProgramService.addProgramAndGetFormMapping(
                new ProgramBuilder().withName(name).withUuid(name).build(), subjectType).getProgram();
    }

    private EncounterType anEncounterType() {
        return encounterTypeRepository.save(new EncounterTypeBuilder()
                .withName(UUID.randomUUID().toString()).withUuid(UUID.randomUUID().toString()).build());
    }

    private void switchApprovalOnFor(FormMapping formMapping) {
        formMapping.setEnableApproval(true);
        formMappingRepository.saveFormMapping(formMapping);
    }

    private void addApprovalEnabledMapping(FormType formType, Program program, EncounterType encounterType) {
        formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(saveFormOfType(formType)).withSubjectType(subjectType)
                .withProgram(program).withEncounterType(encounterType).withEnableApproval(true).build());
    }

    private FormMappingContract requestFor(FormType formType, Program program, EncounterType encounterType) {
        FormMappingContract contract = new FormMappingContract();
        contract.setUuid(UUID.randomUUID().toString());
        contract.setFormUUID(saveFormOfType(formType).getUuid());
        contract.setSubjectTypeUUID(subjectType.getUuid());
        contract.setProgramUUID(program == null ? null : program.getUuid());
        contract.setEncounterTypeUUID(encounterType == null ? null : encounterType.getUuid());
        return contract;
    }

    private void assertRefused(FormMappingContract contract) {
        try {
            formMappingService.createOrUpdateFormMapping(contract);
            fail("Expected the mapping to be refused");
        } catch (ValidationException e) {
            assertTrue("Message must explain why, not fail generically: " + e.getMessage(),
                    e.getMessage().toLowerCase().contains("approval"));
        }
    }

    // AC 1 - attaching works wherever approval is genuinely switched on

    @Test
    public void attachesAnApprovalFormWhereRegistrationHasApprovalSwitchedOn() {
        switchApprovalOnFor(formMappingRepository.getRegistrationFormMapping(subjectType));
        FormMappingContract contract = requestFor(FormType.Approval, null, null);

        formMappingService.createOrUpdateFormMapping(contract);

        assertNotNull(formMappingRepository.findByUuid(contract.getUuid()));
    }

    @Test
    public void attachesARejectionFormOnAProgrammeAndVisitTypeWithApprovalSwitchedOn() {
        Program program = aProgram("p1052a");
        EncounterType encounterType = anEncounterType();
        addApprovalEnabledMapping(FormType.ProgramEncounter, program, encounterType);
        FormMappingContract contract = requestFor(FormType.Rejection, program, encounterType);

        formMappingService.createOrUpdateFormMapping(contract);

        assertNotNull(formMappingRepository.findByUuid(contract.getUuid()));
    }

    // AC 2 - refused where approval is switched off, with a message that says why

    @Test
    public void refusesAnApprovalFormWhereApprovalIsSwitchedOff() {
        assertRefused(requestFor(FormType.Approval, null, null));
    }

    @Test
    public void refusesARejectionFormWhereApprovalIsSwitchedOffOnTheProgramme() {
        assertRefused(requestFor(FormType.Rejection, aProgram("p1052b"), null));
    }

    /**
     * AC 3 - the bug this story exists to prevent. Two production organisations have approval switched on
     * for ManualProgramEnrolmentEligibility, which has no EntityApprovalStatus.EntityType and so never
     * produces an approval to make. Checking enable_approval alone would let this through.
     */
    @Test
    public void refusesAnApprovalFormWhereTheOnlyApprovalEnabledFormIsAnEnrolmentEligibilityForm() {
        Program program = aProgram("p1052c");
        addApprovalEnabledMapping(FormType.ManualProgramEnrolmentEligibility, program, null);

        assertRefused(requestFor(FormType.Approval, program, null));
    }

    @Test
    public void attachesWhenAnEligibilityFormSitsAlongsideARealApprovalEnabledForm() {
        Program program = aProgram("p1052d");
        addApprovalEnabledMapping(FormType.ManualProgramEnrolmentEligibility, program, null);
        switchApprovalOnFor(formMappingRepository.getProgramEnrolmentFormMapping(subjectType, program));
        FormMappingContract contract = requestFor(FormType.Rejection, program, null);

        formMappingService.createOrUpdateFormMapping(contract);

        assertNotNull(formMappingRepository.findByUuid(contract.getUuid()));
    }

    // AC 4 - removing is always allowed, whatever state the rest of the configuration is in

    @Test
    public void allowsRemovingAnApprovalFormAfterApprovalIsSwitchedBackOff() {
        FormMapping registration = formMappingRepository.getRegistrationFormMapping(subjectType);
        switchApprovalOnFor(registration);
        FormMappingContract contract = requestFor(FormType.Approval, null, null);
        formMappingService.createOrUpdateFormMapping(contract);
        registration.setEnableApproval(false);
        formMappingRepository.saveFormMapping(registration);

        contract.setVoided(true);
        formMappingService.createOrUpdateFormMapping(contract);

        assertTrue(formMappingRepository.findByUuid(contract.getUuid()).isVoided());
    }

    @Test
    public void allowsRemovingAnApprovalFormWhoseCombinationNeverQualified() {
        FormMappingContract contract = requestFor(FormType.Approval, null, null);
        contract.setVoided(true);

        formMappingService.createOrUpdateFormMapping(contract);

        assertTrue(formMappingRepository.findByUuid(contract.getUuid()).isVoided());
    }

    // The guard must not touch any other form type

    @Test
    public void doesNotRestrictOrdinaryFormTypes() {
        Program program = aProgram("p1052e");
        FormMappingContract contract = requestFor(FormType.ProgramEncounter, program, anEncounterType());

        formMappingService.createOrUpdateFormMapping(contract);

        assertEquals(FormType.ProgramEncounter,
                formMappingRepository.findByUuid(contract.getUuid()).getForm().getFormType());
    }
}
