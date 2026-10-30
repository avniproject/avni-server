package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.OperationalEncounterTypeRepository;
import org.avni.server.dao.OperationalProgramRepository;
import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestEncounterTypeService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.web.request.webapp.EncounterTypeContractWeb;
import org.avni.server.web.request.webapp.ProgramContractWeb;
import org.avni.server.web.request.webapp.SubjectTypeContractWeb;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AppDesignerListNameSearchIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private SubjectTypeController subjectTypeController;
    @Autowired
    private ProgramController programController;
    @Autowired
    private EncounterTypeController encounterTypeController;
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestEncounterTypeService testEncounterTypeService;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private OperationalProgramRepository operationalProgramRepository;
    @Autowired
    private OperationalSubjectTypeRepository operationalSubjectTypeRepository;
    @Autowired
    private OperationalEncounterTypeRepository operationalEncounterTypeRepository;

    private static final PageRequest FIRST_PAGE_BY_NAME = PageRequest.of(0, 10, Sort.by("name"));

    @Before
    public void setUp() throws Exception {
        super.setUp();
        Group group = testDataSetupService.setupOrganisation().getGroup();
        group.setHasAllPrivileges(true);
        groupRepository.save(group);
    }

    @Test
    public void subjectTypesAreFilteredByNameIgnoringCase() {
        createSubjectType("Household");
        createSubjectType("Child Member");
        createSubjectType("HOUSEHOLD Survey");
        voidOperationalSubjectType(createSubjectType("Old household"));

        assertEquals(List.of("Household", "HOUSEHOLD Survey"), names(subjectTypeController.getAll("houseHOLD", FIRST_PAGE_BY_NAME), st -> st.getName()));
        assertEquals(3, names(subjectTypeController.getAll(null, FIRST_PAGE_BY_NAME), st -> st.getName()).size());
        assertEquals(3, names(subjectTypeController.getAll("  ", FIRST_PAGE_BY_NAME), st -> st.getName()).size());
        assertEquals(List.of(), names(subjectTypeController.getAll("zzz", FIRST_PAGE_BY_NAME), st -> st.getName()));
    }

    @Test
    public void subjectTypeSearchPagesOverMatchesAndCountsOnlyMatches() {
        for (int i = 1; i <= 12; i++) createSubjectType(String.format("Ward %02d", i));
        createSubjectType("Village");

        CollectionModel<EntityModel<SubjectTypeContractWeb>> secondPage = subjectTypeController.getAll("ward", PageRequest.of(1, 10, Sort.by("name")));
        assertEquals(12, totalElements(secondPage));
        assertEquals(List.of("Ward 11", "Ward 12"), names(secondPage, st -> st.getName()));

        CollectionModel<EntityModel<SubjectTypeContractWeb>> descending = subjectTypeController.getAll("ward", PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "name")));
        assertEquals(List.of("Ward 12", "Ward 11", "Ward 10"), names(descending, st -> st.getName()));
    }

    @Test
    public void programsAreFilteredByNameIgnoringCase() {
        createProgram("Pregnancy");
        createProgram("Child");
        createProgram("Post PREGNANCY care");
        voidOperationalProgram(createProgram("Pregnancy old"));

        CollectionModel<EntityModel<ProgramContractWeb>> result = programController.getAll("pregnancy", FIRST_PAGE_BY_NAME);
        assertEquals(List.of("Post PREGNANCY care", "Pregnancy"), names(result, ProgramContractWeb::getName));
        assertEquals(2, totalElements(result));
        assertEquals(3, names(programController.getAll(null, FIRST_PAGE_BY_NAME), ProgramContractWeb::getName).size());
    }

    @Test
    public void encounterTypesAreFilteredByNameIgnoringCase() {
        SubjectType subjectType = createSubjectType("Individual");
        testEncounterTypeService.addGeneralEncounterTypeAndGetFormMapping("ANC Visit", subjectType);
        testEncounterTypeService.addGeneralEncounterTypeAndGetFormMapping("PNC Visit", subjectType);
        testEncounterTypeService.addGeneralEncounterTypeAndGetFormMapping("Growth Monitoring", subjectType);
        testEncounterTypeService.addGeneralEncounterTypeAndGetFormMapping("Old ANC", subjectType);
        OperationalEncounterType oldAnc = operationalEncounterTypeRepository.findAll().stream().filter(oet -> oet.getName().equals("Old ANC")).findFirst().orElseThrow();
        oldAnc.setVoided(true);
        operationalEncounterTypeRepository.save(oldAnc);

        CollectionModel<EntityModel<EncounterTypeContractWeb>> result = encounterTypeController.getAll("visit", FIRST_PAGE_BY_NAME);
        assertEquals(List.of("ANC Visit", "PNC Visit"), names(result, EncounterTypeContractWeb::getName));
        assertEquals(List.of("ANC Visit"), names(encounterTypeController.getAll("anc", FIRST_PAGE_BY_NAME), EncounterTypeContractWeb::getName));
        assertEquals(3, names(encounterTypeController.getAll(null, FIRST_PAGE_BY_NAME), EncounterTypeContractWeb::getName).size());
    }

    private SubjectType createSubjectType(String name) {
        return testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setName(name).build());
    }

    private void voidOperationalSubjectType(SubjectType subjectType) {
        OperationalSubjectType operationalSubjectType = operationalSubjectTypeRepository.findAll().stream()
                .filter(ost -> ost.getSubjectType().getId().equals(subjectType.getId())).findFirst().orElseThrow();
        operationalSubjectType.setVoided(true);
        operationalSubjectTypeRepository.save(operationalSubjectType);
    }

    private Program createProgram(String name) {
        Program program = programRepository.save(new ProgramBuilder().withName(name).build());
        OperationalProgram operationalProgram = new OperationalProgram();
        operationalProgram.setName(name);
        operationalProgram.setProgram(program);
        operationalProgram.setUuid(UUID.randomUUID().toString());
        operationalProgramRepository.save(operationalProgram);
        return program;
    }

    private void voidOperationalProgram(Program program) {
        OperationalProgram operationalProgram = operationalProgramRepository.findAll().stream()
                .filter(op -> op.getProgram().getId().equals(program.getId())).findFirst().orElseThrow();
        operationalProgram.setVoided(true);
        operationalProgramRepository.save(operationalProgram);
    }

    private static <T> List<String> names(CollectionModel<EntityModel<T>> page, Function<T, String> name) {
        return page.getContent().stream().map(EntityModel::getContent).map(name).toList();
    }

    private static long totalElements(CollectionModel<?> page) {
        return ((PagedModel<?>) page).getMetadata().getTotalElements();
    }
}
