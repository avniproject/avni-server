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
import org.avni.server.importer.batch.zip.BundleZipFileImporter;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * #1052 - an Approval or Rejection form may only be attached where an approval can actually happen.
 * App Designer (FormController, FormMappingController) and bundle import (BundleZipFileImporter) both
 * reach this through createOrUpdateFormMapping, so guarding there covers both routes.
 * <p>
 * Approval is switched on by updating the mapping a combination already has, never by adding a second
 * one of the same form type - that is what an administrator does, and a second one of the same type is
 * refused outright, named rather than as a database error.
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

    // withDefaultFieldsForNewEntity names the form after a random uuid, which cannot be asserted on. The
    // duplicate message exists to name the form already holding the combination, so these tests need one
    // with a name a human would recognise.
    private FormMappingContract requestForNamedForm(FormType formType, String formName, Program program) {
        Form form = formRepository.save(new TestFormBuilder()
                .withDefaultFieldsForNewEntity().withFormType(formType).withName(formName).build());
        FormMappingContract contract = new FormMappingContract();
        contract.setUuid(UUID.randomUUID().toString());
        contract.setFormUUID(form.getUuid());
        contract.setSubjectTypeUUID(subjectType.getUuid());
        contract.setProgramUUID(program == null ? null : program.getUuid());
        return contract;
    }

    private void assertRefused(FormMappingContract contract) {
        assertRefusedWith(contract, "approval");
    }

    /**
     * The message is the whole point of the refusal - an administrator told only that something is wrong
     * has to guess which of six forms to go and change. Asserting the naming here is what stops the
     * wording drifting back to a generic sentence.
     */
    private void assertRefusedWith(FormMappingContract contract, String expectedFragment) {
        try {
            formMappingService.createOrUpdateFormMapping(contract);
            fail("Expected the mapping to be refused");
        } catch (ValidationException e) {
            assertTrue("Message must name what to fix, got: " + e.getMessage(),
                    e.getMessage().toLowerCase().contains(expectedFragment.toLowerCase()));
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
        assertRefusedWith(requestFor(FormType.Approval, null, null), "st1052 registration form");
    }

    @Test
    public void refusesARejectionFormWhereApprovalIsSwitchedOffOnTheProgramme() {
        assertRefusedWith(requestFor(FormType.Rejection, aProgram("p1052b"), null),
                "p1052b enrolment or exit form for st1052");
    }

    @Test
    public void refusesAnApprovalFormOnAVisitTypeAndNamesThatVisitType() {
        EncounterType encounterType = encounterTypeRepository.save(
                new EncounterTypeBuilder().withName("et1052").withUuid("et1052").build());

        assertRefusedWith(requestFor(FormType.Approval, null, encounterType),
                "et1052 visit or visit cancellation form for st1052");
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

        // The switch is on, just on a form that cannot produce an approval - so the message must not
        // claim approval is switched off, which is what the administrator would reasonably dispute.
        assertRefusedWith(requestFor(FormType.Approval, program, null), "never produces an approval");
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

    /**
     * A second form of the same type on one combination.
     *
     * check_form_mapping_uniqueness catches this too, but raises a bare plpgsql error: it arrives as a
     * JpaSystemException, misses the constraint-violation handler and reaches App Designer as a 500 reading
     * "Duplicate form mapping exists for: organisation_id: 586, subject_type_id: 3194 ...". Refusing it here
     * is only worth doing if the message names something the administrator can act on, which is what these
     * assert. The client cannot catch this case at all - FormSettings compares a form's mappings against
     * each other, and the clash is with a mapping belonging to a different form.
     */
    @Test
    public void refusesASecondFormOfTheSameTypeAndNamesTheOneAlreadyAttached() {
        switchApprovalOnFor(formMappingRepository.getRegistrationFormMapping(subjectType));
        formMappingService.createOrUpdateFormMapping(
                requestForNamedForm(FormType.Approval, "Screening Approval", null));

        assertRefusedWith(requestForNamedForm(FormType.Approval, "Another Approval", null), "Screening Approval");
    }

    @Test
    public void namesTheCombinationTheDuplicateIsOn() {
        switchApprovalOnFor(formMappingRepository.getRegistrationFormMapping(subjectType));
        formMappingService.createOrUpdateFormMapping(requestForNamedForm(FormType.Approval, "First Approval", null));

        assertRefusedWith(requestForNamedForm(FormType.Approval, "Second Approval", null), "st1052 registration form");
    }

    /**
     * The null handling that made a new repository method necessary. getRequiredFormMapping treats a null
     * programme as "any programme", so reusing it would report a subject-only decision form as a duplicate
     * of a programme-level one. The database function matches null to null, and so must this.
     */
    @Test
    public void doesNotTreatASubjectOnlyMappingAsADuplicateOfAProgrammeOne() {
        Program program = aProgram("p1052e");
        switchApprovalOnFor(formMappingRepository.getRegistrationFormMapping(subjectType));
        switchApprovalOnFor(formMappingRepository.getProgramEnrolmentFormMapping(subjectType, program));
        formMappingService.createOrUpdateFormMapping(requestFor(FormType.Approval, program, null));

        FormMappingContract subjectOnly = requestFor(FormType.Approval, null, null);
        formMappingService.createOrUpdateFormMapping(subjectOnly);

        assertNotNull("a subject-only mapping is a different combination, not a duplicate",
                formMappingRepository.findByUuid(subjectOnly.getUuid()));
    }

    @Test
    public void allowsADifferentFormTypeOnTheSameCombination() {
        switchApprovalOnFor(formMappingRepository.getRegistrationFormMapping(subjectType));
        formMappingService.createOrUpdateFormMapping(requestFor(FormType.Approval, null, null));

        FormMappingContract rejection = requestFor(FormType.Rejection, null, null);
        formMappingService.createOrUpdateFormMapping(rejection);

        assertNotNull("an approval and a rejection form share a combination by design",
                formMappingRepository.findByUuid(rejection.getUuid()));
    }

    /**
     * Two live mappings can already share a combination, because changing a form's type does not re-run
     * the constraint on form_mapping. Checking every resubmitted row made both of those forms permanently
     * uneditable through App Designer: FormSettings resubmits all of a form's mappings on every save, the
     * clash is with a mapping belonging to the *other* form, and this request cannot void that one.
     */
    @Test
    public void allowsResavingAnUnchangedMappingThatAlreadyClashes() {
        EncounterType encounterType = anEncounterType();
        formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(saveFormOfType(FormType.Encounter)).withSubjectType(subjectType)
                .withEncounterType(encounterType).build());

        Form cancellationForm = saveFormOfType(FormType.IndividualEncounterCancellation);
        FormMapping clashing = new FormMappingBuilder()
                .withForm(cancellationForm).withSubjectType(subjectType)
                .withEncounterType(encounterType).build();
        formMappingRepository.saveFormMapping(clashing);

        // Retyped straight through the repository, which is what updateMetadata does to the form row. The
        // constraint lives on form_mapping and does not re-run, so two live Encounter mappings now sit on
        // one combination - the state this test exists to make survivable.
        cancellationForm.setFormType(FormType.Encounter);
        formRepository.save(cancellationForm);

        formMappingService.createOrUpdateFormMapping(FormMappingContract.fromFormMapping(clashing));

        assertNotNull(formMappingRepository.findByUuid(clashing.getUuid()));
    }

    /** Saving an existing mapping again must not report it as a duplicate of itself. */
    @Test
    public void doesNotTreatUpdatingAMappingAsItsOwnDuplicate() {
        switchApprovalOnFor(formMappingRepository.getRegistrationFormMapping(subjectType));
        FormMappingContract contract = requestFor(FormType.Approval, null, null);
        formMappingService.createOrUpdateFormMapping(contract);

        formMappingService.createOrUpdateFormMapping(contract);

        assertNotNull(formMappingRepository.findByUuid(contract.getUuid()));
    }

    /** A mapping the administrator has already removed must not block its replacement. */
    @Test
    public void doesNotCountAVoidedMappingAsADuplicate() {
        switchApprovalOnFor(formMappingRepository.getRegistrationFormMapping(subjectType));
        FormMappingContract first = requestFor(FormType.Approval, null, null);
        formMappingService.createOrUpdateFormMapping(first);
        first.setVoided(true);
        formMappingService.createOrUpdateFormMapping(first);

        FormMappingContract replacement = requestFor(FormType.Approval, null, null);
        formMappingService.createOrUpdateFormMapping(replacement);

        assertNotNull(formMappingRepository.findByUuid(replacement.getUuid()));
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

    // AC 5 - the rule applies to bundle import too, and must not depend on the order inside the file

    /**
     * formMappings.json is one array, processed element by element in file order (BundleZipFileImporter,
     * "formMappings.json" case). This is the ordering that breaks: the Rejection mapping is listed before
     * the enrolment form whose approval switch it depends on. Without the sort the whole bundle import
     * fails - see theSameOrderIsRefusedWithoutTheSort below, which pins that this test is not vacuous.
     */
    @Test
    public void aBundleSucceedsWhenTheRejectionMappingIsListedBeforeTheFormItDependsOn() {
        Program program = aProgram("p1052f");
        FormMappingContract enrolment = FormMappingContract.fromFormMapping(
                formMappingRepository.getProgramEnrolmentFormMapping(subjectType, program));
        enrolment.setEnableApproval(true);
        FormMappingContract rejection = requestFor(FormType.Rejection, program, null);
        rejection.setFormType(FormType.Rejection);

        BundleZipFileImporter.approvalDecisionFormsLast(Arrays.asList(rejection, enrolment))
                .forEach(formMappingService::createOrUpdateFormMapping);

        assertNotNull(formMappingRepository.findByUuid(rejection.getUuid()));
        assertTrue(formMappingRepository.findByUuid(enrolment.getUuid()).isEnableApproval());
    }

    @Test
    public void theSameOrderIsRefusedWithoutTheSort() {
        Program program = aProgram("p1052g");
        FormMappingContract rejection = requestFor(FormType.Rejection, program, null);
        rejection.setFormType(FormType.Rejection);

        assertRefused(rejection);
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

    // Findings from ombhardwajj's review of #1052, 2 Sep 2026

    /**
     * Finding 1. form_mapping.form_id is nullable (V1_146) and createOrUpdateEmptyFormMapping sets it to
     * null explicitly, while findApprovalEnabledMappingsForCombination filters only on enableApproval,
     * isVoided and implVersion - so a form-less approval-enabled row is returned to the guard. Without a
     * null check the anyMatch dereferences it and the admin gets a 500 instead of the validation message.
     */
    @Test
    public void doesNotFallOverWhenAnApprovalEnabledSiblingHasNoForm() {
        formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(null).withSubjectType(subjectType).withEnableApproval(true).build());

        assertRefused(requestFor(FormType.Approval, null, null));
    }

    /**
     * The same row must not mask a genuine approval-enabled sibling either: the form-less row is skipped,
     * not treated as disqualifying.
     */
    @Test
    public void aFormLessSiblingDoesNotHideARealApprovalEnabledOne() {
        formMappingRepository.saveFormMapping(new FormMappingBuilder()
                .withForm(null).withSubjectType(subjectType).withEnableApproval(true).build());
        switchApprovalOnFor(formMappingRepository.getRegistrationFormMapping(subjectType));

        FormMappingContract contract = requestFor(FormType.Approval, null, null);
        formMappingService.createOrUpdateFormMapping(contract);

        assertNotNull(formMappingRepository.findByUuid(contract.getUuid()));
    }

    /**
     * Finding 4. POST /emptyFormMapping reaches createOrUpdateEmptyFormMapping, which set subject type,
     * programme, visit type, voided and enableApproval and saved with no guard at all - so it could create
     * exactly the orphaned decision form this story exists to refuse.
     */
    @Test
    public void refusesAnApprovalFormThroughTheEmptyFormMappingRoute() {
        FormMappingContract contract = requestFor(FormType.Approval, null, null);

        try {
            formMappingService.createOrUpdateEmptyFormMapping(contract);
            fail("Expected the mapping to be refused");
        } catch (ValidationException e) {
            assertTrue("Message must explain why: " + e.getMessage(),
                    e.getMessage().toLowerCase().contains("approval"));
        }
    }

    @Test
    public void allowsAnApprovalFormThroughTheEmptyFormMappingRouteWhereApprovalIsOn() {
        switchApprovalOnFor(formMappingRepository.getRegistrationFormMapping(subjectType));
        FormMappingContract contract = requestFor(FormType.Approval, null, null);

        formMappingService.createOrUpdateEmptyFormMapping(contract);

        assertNotNull(formMappingRepository.findByUuid(contract.getUuid()));
    }

    /**
     * A form-less mapping through this route carries no form type, so there is nothing to guard - and
     * refusing it would break the route's actual purpose.
     */
    @Test
    public void stillAllowsAFormLessMappingThroughTheEmptyFormMappingRoute() {
        FormMappingContract contract = requestFor(FormType.Approval, null, null);
        contract.setFormUUID(null);

        formMappingService.createOrUpdateEmptyFormMapping(contract);

        assertNotNull(formMappingRepository.findByUuid(contract.getUuid()));
    }

    /**
     * Finding 6. The message is shown to a non-developer administrator in App Designer, so it has to read
     * as English - "a Approval form" does not.
     */
    @Test
    public void namesTheFormTypeWithTheRightArticle() {
        try {
            formMappingService.createOrUpdateFormMapping(requestFor(FormType.Approval, null, null));
            fail("Expected the mapping to be refused");
        } catch (ValidationException e) {
            assertTrue("Should read 'an Approval form', got: " + e.getMessage(),
                    e.getMessage().contains("an Approval form"));
        }
        try {
            formMappingService.createOrUpdateFormMapping(requestFor(FormType.Rejection, null, null));
            fail("Expected the mapping to be refused");
        } catch (ValidationException e) {
            assertTrue("Should read 'a Rejection form', got: " + e.getMessage(),
                    e.getMessage().contains("a Rejection form"));
        }
    }

    /**
     * Finding 5, characterised rather than fixed. The invariant is enforced on the way in only: nothing
     * stops an administrator switching approval back off on the sibling, or voiding it, once a decision
     * form is attached - leaving exactly the orphan #1052 refuses to let anyone create.
     *
     * These two tests assert what the code does today, so the gap is visible and pinned rather than
     * implicit. Whether to warn on disable, cascade-void the decision form, or accept the state is a
     * product call, recorded in avni-product-ops context/open-decisions.md. When it is made, these tests
     * are the ones to change.
     */
    @Test
    public void currentlyAllowsApprovalToBeSwitchedOffUnderAnAttachedDecisionForm() {
        FormMapping registration = formMappingRepository.getRegistrationFormMapping(subjectType);
        switchApprovalOnFor(registration);
        FormMappingContract approvalForm = requestFor(FormType.Approval, null, null);
        formMappingService.createOrUpdateFormMapping(approvalForm);

        registration.setEnableApproval(false);
        formMappingRepository.saveFormMapping(registration);

        assertFalse("approval can still be switched off underneath the decision form",
                formMappingRepository.getRegistrationFormMapping(subjectType).isEnableApproval());
        assertNotNull("and the decision form is left attached to a combination that can no longer approve",
                formMappingRepository.findByUuid(approvalForm.getUuid()));
        assertFalse(formMappingRepository.findByUuid(approvalForm.getUuid()).isVoided());
    }

    @Test
    public void currentlyAllowsTheApprovalEnabledSiblingToBeVoidedUnderAnAttachedDecisionForm() {
        FormMapping registration = formMappingRepository.getRegistrationFormMapping(subjectType);
        switchApprovalOnFor(registration);
        FormMappingContract approvalForm = requestFor(FormType.Approval, null, null);
        formMappingService.createOrUpdateFormMapping(approvalForm);

        registration.setVoided(true);
        formMappingRepository.saveFormMapping(registration);

        assertNotNull("the decision form survives its sibling being voided",
                formMappingRepository.findByUuid(approvalForm.getUuid()));
        assertFalse(formMappingRepository.findByUuid(approvalForm.getUuid()).isVoided());
    }
}
