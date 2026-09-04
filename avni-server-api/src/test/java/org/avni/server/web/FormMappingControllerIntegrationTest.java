package org.avni.server.web;

import org.avni.server.application.Form;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.Program;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.factory.access.TestGroupPrivilegeBuilder;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * #1052 review finding 3 (ombhardwajj, 2 Sep 2026).
 *
 * The ordering fix from #1052 was applied to bundle import only. POST /formMappings takes a List and
 * iterates it in request order inside one transaction, calling the same createOrUpdateFormMapping - so the
 * exact input the bundle fix exists to tolerate, a decision form listed before the mapping that switches
 * approval on, still failed the whole POST here. Not used by avni-webapp, but it is a live
 * implementation-tooling route.
 *
 * These live in the controller's package because save is package-private; making it public purely for a
 * test would be the wrong trade.
 */
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class FormMappingControllerIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private FormMappingController formMappingController;
    @Autowired
    private FormMappingRepository formMappingRepository;
    @Autowired
    private FormRepository formRepository;

    private SubjectType subjectType;

    @Before
    public void setup() {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        Map<GroupPrivilege, PrivilegeType> privileges = new HashMap<>();
        privileges.put(new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().build(), PrivilegeType.EditApproval);
        privileges.put(new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().build(), PrivilegeType.EditRejection);
        privileges.put(new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().build(), PrivilegeType.EditSubjectType);
        privileges.put(new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().build(), PrivilegeType.EditProgram);
        testGroupService.updateGroup(organisationData.getGroup(), privileges);
        setUser(organisationData.getUser().getUsername());
        subjectType = testSubjectTypeService.createWithDefaultsAndGetFormMapping(
                new SubjectTypeBuilder().setMandatoryFieldsForNewEntity()
                        .setUuid("st1052c").setName("st1052c").build()).getSubjectType();
    }

    private Form saveFormOfType(FormType formType) {
        return formRepository.save(new TestFormBuilder().withDefaultFieldsForNewEntity().withFormType(formType).build());
    }

    private FormMappingContract requestFor(FormType formType, Program program) {
        FormMappingContract contract = new FormMappingContract();
        contract.setUuid(UUID.randomUUID().toString());
        contract.setFormUUID(saveFormOfType(formType).getUuid());
        contract.setSubjectTypeUUID(subjectType.getUuid());
        contract.setProgramUUID(program == null ? null : program.getUuid());
        contract.setFormType(formType);
        return contract;
    }

    @Test
    public void acceptsARejectionListedBeforeTheFormItDependsOn() {
        Program program = testProgramService.addProgramAndGetFormMapping(
                new ProgramBuilder().withName("p1052h").withUuid("p1052h").build(), subjectType).getProgram();
        FormMappingContract enrolment = FormMappingContract.fromFormMapping(
                formMappingRepository.getProgramEnrolmentFormMapping(subjectType, program));
        enrolment.setEnableApproval(true);
        FormMappingContract rejection = requestFor(FormType.Rejection, program);

        formMappingController.save(Arrays.asList(rejection, enrolment));

        assertNotNull("the rejection mapping should have been saved despite being listed first",
                formMappingRepository.findByUuid(rejection.getUuid()));
        assertTrue(formMappingRepository.findByUuid(enrolment.getUuid()).isEnableApproval());
    }

    /**
     * The sort reorders; it does not excuse. A decision form on a combination where no approval can ever
     * happen is still refused, so the fix cannot mask the condition #1052 exists to catch.
     */
    @Test
    public void stillRefusesADecisionFormWhereNoSiblingCanProduceAnApproval() {
        FormMappingContract rejection = requestFor(FormType.Rejection, null);

        try {
            formMappingController.save(Collections.singletonList(rejection));
            fail("Expected the mapping to be refused");
        } catch (ValidationException e) {
            assertTrue("Message must explain why: " + e.getMessage(),
                    e.getMessage().toLowerCase().contains("approval"));
        }
    }
}
