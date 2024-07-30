package org.avni.server.framework.jpa;

import org.avni.server.dao.*;
import org.avni.server.dao.application.*;
import org.avni.server.dao.individualRelationship.IndividualRelationGenderMappingRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationshipTypeRepository;
import org.avni.server.dao.task.TaskStatusRepository;
import org.avni.server.dao.task.TaskTypeRepository;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class AvniRestRepositoryTest {
    @Test
    public void cannotHaveOverloadedMethodsIfRestRepositoryIsExported() {
        assertNoDuplicates(UserRepository.class);
        assertNoDuplicates(FormElementGroupRepository.class);
        assertNoDuplicates(FormElementRepository.class);
        assertNoDuplicates(FormMappingRepository.class);
        assertNoDuplicates(FormRepository.class);
        assertNoDuplicates(MenuItemRepository.class);
        assertNoDuplicates(IndividualRelationGenderMappingRepository.class);
        assertNoDuplicates(IndividualRelationRepository.class);
        assertNoDuplicates(IndividualRelationshipTypeRepository.class);
        assertNoDuplicates(TaskStatusRepository.class);
        assertNoDuplicates(TaskTypeRepository.class);
        assertNoDuplicates(AccountAdminRepository.class);
        assertNoDuplicates(AccountRepository.class);
        assertNoDuplicates(AddressLevelTypeRepository.class);
        assertNoDuplicates(ApprovalStatusRepository.class);
        assertNoDuplicates(CatchmentRepository.class);
        assertNoDuplicates(ChecklistDetailRepository.class);
        assertNoDuplicates(ChecklistItemDetailRepository.class);
        assertNoDuplicates(ConceptAnswerRepository.class);
        assertNoDuplicates(ConceptRepository.class);
        assertNoDuplicates(DashboardFilterRepository.class);
        assertNoDuplicates(DashboardRepository.class);
        assertNoDuplicates(DashboardSectionCardMappingRepository.class);
        assertNoDuplicates(DashboardSectionRepository.class);
        assertNoDuplicates(EncounterTypeRepository.class);
        assertNoDuplicates(EntityApprovalStatusRepository.class);
        assertNoDuplicates(GenderRepository.class);
        assertNoDuplicates(GroupDashboardRepository.class);
        assertNoDuplicates(GroupPrivilegeRepository.class);
        assertNoDuplicates(GroupRepository.class);
        assertNoDuplicates(GroupRoleRepository.class);
        assertNoDuplicates(IdentifierAssignmentRepository.class);
        assertNoDuplicates(IdentifierUserAssignmentRepository.class);
        assertNoDuplicates(LocationRepository.class);
        assertNoDuplicates(NewsRepository.class);
        assertNoDuplicates(OperationalEncounterTypeRepository.class);
        assertNoDuplicates(OperationalProgramRepository.class);
        assertNoDuplicates(OperationalSubjectTypeRepository.class);
        assertNoDuplicates(OrganisationConfigRepository.class);
        assertNoDuplicates(OrganisationGroupRepository.class);
        assertNoDuplicates(OrganisationRepository.class);
        assertNoDuplicates(PrivilegeRepository.class);
        assertNoDuplicates(ProgramRepository.class);
        assertNoDuplicates(RuleDependencyRepository.class);
        assertNoDuplicates(RuleRepository.class);
        assertNoDuplicates(StandardReportCardTypeRepository.class);
        assertNoDuplicates(SubjectTypeRepository.class);
        assertNoDuplicates(TranslationRepository.class);
        assertNoDuplicates(UserGroupRepository.class);
        assertNoDuplicates(VideoRepository.class);
        assertNoDuplicates(VideoTelemetricRepository.class);
    }

    private static void assertNoDuplicates(Class clas) {
        List<String> duplicates = Arrays.stream(clas.getMethods())
                .filter(method -> !method.isDefault() && method.getDeclaringClass().equals(clas))
                .collect(Collectors.groupingBy(Method::getName))
                .entrySet()
                .stream()
                .filter(p -> p.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        duplicates.size();
        assertEquals(0, duplicates.size());
    }
}
