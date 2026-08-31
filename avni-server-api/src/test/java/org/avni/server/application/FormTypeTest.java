package org.avni.server.application;

import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FormTypeTest {
    @Test
    public void getPrivilegeTypeShouldWorkForAllFormTypes() {
        Arrays.stream(FormType.values()).forEach(formType -> assertNotNull(FormType.getPrivilegeType(formType)));
    }

    @Test
    public void approvalAndRejectionFormsAreEditedUnderTheirOwnPrivileges() {
        assertEquals(PrivilegeType.EditApproval, FormType.getPrivilegeType(FormType.Approval));
        assertEquals(PrivilegeType.EditRejection, FormType.getPrivilegeType(FormType.Rejection));
    }

    @Test
    public void formTypesThatProduceAnApprovalResolveToTheirEntityApprovalStatusEntityType() {
        assertEquals(EntityApprovalStatus.EntityType.Subject, FormType.IndividualProfile.getApprovalEntityType());
        assertEquals(EntityApprovalStatus.EntityType.ProgramEnrolment, FormType.ProgramEnrolment.getApprovalEntityType());
        assertEquals(EntityApprovalStatus.EntityType.ProgramEnrolment, FormType.ProgramExit.getApprovalEntityType());
        assertEquals(EntityApprovalStatus.EntityType.Encounter, FormType.Encounter.getApprovalEntityType());
        assertEquals(EntityApprovalStatus.EntityType.Encounter, FormType.IndividualEncounterCancellation.getApprovalEntityType());
        assertEquals(EntityApprovalStatus.EntityType.ProgramEncounter, FormType.ProgramEncounter.getApprovalEntityType());
        assertEquals(EntityApprovalStatus.EntityType.ProgramEncounter, FormType.ProgramEncounterCancellation.getApprovalEntityType());
        assertEquals(EntityApprovalStatus.EntityType.ChecklistItem, FormType.ChecklistItem.getApprovalEntityType());
    }

    /**
     * The specific bug #1052 exists to prevent. ManualProgramEnrolmentEligibility carries
     * enable_approval = true in two production organisations, but has no EntityApprovalStatus.EntityType,
     * so it never creates an approval for anyone to decide on. Checking enable_approval alone would let
     * an approval form be attached where no approval can ever happen.
     */
    @Test
    public void formTypesThatNeverProduceAnApprovalResolveToNull() {
        assertNull(FormType.ManualProgramEnrolmentEligibility.getApprovalEntityType());
        assertNull(FormType.SubjectEnrolmentEligibility.getApprovalEntityType());
        assertNull(FormType.BeneficiaryIdentification.getApprovalEntityType());
        assertNull(FormType.IndividualRelationship.getApprovalEntityType());
        assertNull(FormType.Location.getApprovalEntityType());
        assertNull(FormType.Task.getApprovalEntityType());
        assertNull(FormType.Approval.getApprovalEntityType());
        assertNull(FormType.Rejection.getApprovalEntityType());
    }

    @Test
    public void onlyApprovalAndRejectionAreApprovalDecisionForms() {
        assertTrue(FormType.Approval.isApprovalDecisionForm());
        assertTrue(FormType.Rejection.isApprovalDecisionForm());
        Arrays.stream(FormType.values())
                .filter(formType -> formType != FormType.Approval && formType != FormType.Rejection)
                .forEach(formType -> assertFalse(formType.name(), formType.isApprovalDecisionForm()));
    }
}
