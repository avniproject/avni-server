package org.avni.server.service;

import jakarta.transaction.Transactional;
import org.avni.server.application.FormMapping;
import org.avni.server.application.Subject;
import org.avni.server.dao.ChecklistDetailRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.domain.ChecklistDetail;
import org.avni.server.domain.OperationalSubjectType;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.SyncableItem;
import org.avni.server.domain.accessControl.GroupPrivileges;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.accessControl.GroupPrivilegeService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SyncDetailsService {
    private final OperationalSubjectTypeRepository subjectTypeRepository;
    private final FormMappingRepository formMappingRepository;
    private final ChecklistDetailRepository checklistDetailRepository;
    private final OrganisationConfigService organisationConfigService;
    private final GroupPrivilegeService groupPrivilegeService;


    public SyncDetailsService(OperationalSubjectTypeRepository subjectTypeRepository1, EncounterTypeRepository encounterTypeRepository, FormMappingRepository formMappingRepository, ChecklistDetailRepository checklistDetailRepository, OrganisationConfigService organisationConfigService, GroupPrivilegeService groupPrivilegeService) {
        this.subjectTypeRepository = subjectTypeRepository1;
        this.formMappingRepository = formMappingRepository;
        this.checklistDetailRepository = checklistDetailRepository;
        this.organisationConfigService = organisationConfigService;
        this.groupPrivilegeService = groupPrivilegeService;
    }

    @Transactional
    public Set<SyncableItem> getAllSyncableItems(boolean scopeAwareEAS, boolean includeUserSubjectType) {
        List<SubjectType> subjectTypes = subjectTypeRepository.findAll()
                .stream()
                .filter(st -> !st.getSubjectType().getType().equals(Subject.User) || includeUserSubjectType)
                .map(OperationalSubjectType::getSubjectType)
                .collect(Collectors.toList());

        List<FormMapping> generalEncounters = formMappingRepository.getAllGeneralEncounterFormMappings();
        List<FormMapping> programEncounters = formMappingRepository.getAllProgramEncounterFormMappings();
        List<FormMapping> programEnrolments = formMappingRepository.getAllProgramEnrolmentFormMappings();
        List<FormMapping> allRegistrationFormMappings = formMappingRepository.getAllRegistrationFormMappings();
        List<ChecklistDetail> checklistDetails = checklistDetailRepository.findAll();
        GroupPrivileges groupPrivileges = groupPrivilegeService.getGroupPrivileges();

        HashSet<SyncableItem> syncableItems = new HashSet<>();

        subjectTypes.forEach(subjectType -> {
            if (!groupPrivileges.hasPrivilege(PrivilegeType.ViewSubject, subjectType, null, null, null)) {
                return;
            }
            addToSyncableItems(syncableItems, SyncEntityName.Individual, subjectType.getUuid());
            addToSyncableItems(syncableItems, SyncEntityName.SubjectMigration, subjectType.getUuid());
            addToSyncableItems(syncableItems, SyncEntityName.SubjectProgramEligibility, subjectType.getUuid());
            if (subjectType.isPerson()) {
                addToSyncableItems(syncableItems, SyncEntityName.IndividualRelationship, subjectType.getUuid());
            }
            if (subjectType.isGroup()) {
                addToSyncableItems(syncableItems, SyncEntityName.GroupSubject, subjectType.getUuid());
            }
            if (organisationConfigService.isCommentEnabled()) {
                addToSyncableItems(syncableItems, SyncEntityName.Comment, subjectType.getUuid());
                addToSyncableItems(syncableItems, SyncEntityName.CommentThread, subjectType.getUuid());
            }

            Optional<FormMapping> subjectTypeFormMapping = allRegistrationFormMappings.stream().filter((formMapping -> Objects.equals(formMapping.getSubjectType().getId(), subjectType.getId()))).findFirst();
            if (scopeAwareEAS && subjectTypeFormMapping.isPresent() && subjectTypeFormMapping.get().isEnableApproval())
                addToSyncableItems(syncableItems, SyncEntityName.SubjectEntityApprovalStatus, subjectType.getUuid());

        });
        generalEncounters.forEach(formMapping -> {
            if (!groupPrivileges.hasPrivilege(PrivilegeType.ViewVisit, formMapping.getSubjectType(), null, formMapping.getEncounterType(), null)) {
                return;
            }
            addToSyncableItems(syncableItems, SyncEntityName.Encounter, formMapping.getEncounterTypeUuid());
            if (scopeAwareEAS && formMapping.isEnableApproval())
                addToSyncableItems(syncableItems, SyncEntityName.EncounterEntityApprovalStatus, formMapping.getEncounterTypeUuid());
        });
        programEncounters.forEach(formMapping -> {
            if (!groupPrivileges.hasPrivilege(PrivilegeType.ViewVisit, formMapping.getSubjectType(), formMapping.getProgram(), formMapping.getEncounterType(), null)) {
                return;
            }
            addToSyncableItems(syncableItems, SyncEntityName.ProgramEncounter, formMapping.getEncounterTypeUuid());
            if (scopeAwareEAS && formMapping.isEnableApproval())
                addToSyncableItems(syncableItems, SyncEntityName.ProgramEncounterEntityApprovalStatus, formMapping.getEncounterTypeUuid());
        });
        programEnrolments.forEach(formMapping -> {
            if (!groupPrivileges.hasPrivilege(PrivilegeType.ViewEnrolmentDetails, formMapping.getSubjectType(), formMapping.getProgram(), formMapping.getEncounterType(), null)) {
                return;
            }
            addToSyncableItems(syncableItems, SyncEntityName.ProgramEnrolment, formMapping.getProgramUuid());
            if (scopeAwareEAS && formMapping.isEnableApproval())
                addToSyncableItems(syncableItems, SyncEntityName.ProgramEnrolmentEntityApprovalStatus, formMapping.getProgramUuid());
        });

        checklistDetails.forEach(checklistDetail -> {
            if (subjectTypes.stream().anyMatch(subjectType -> groupPrivileges.hasPrivilege(PrivilegeType.ViewChecklist, subjectType, null, null, checklistDetail))) {
                addToSyncableItems(syncableItems, SyncEntityName.Checklist, checklistDetail.getUuid());
                addToSyncableItems(syncableItems, SyncEntityName.ChecklistItem, checklistDetail.getUuid());
            }
        });
        addToSyncableItems(syncableItems, Arrays.asList(SyncEntityName.IdentifierAssignment, SyncEntityName.ChecklistDetail, SyncEntityName.Rule, SyncEntityName.RuleDependency,
                SyncEntityName.Form, SyncEntityName.FormMapping, SyncEntityName.EncounterType, SyncEntityName.Program, SyncEntityName.Gender,
                SyncEntityName.IndividualRelation, SyncEntityName.IndividualRelationGenderMapping, SyncEntityName.IndividualRelationshipType, SyncEntityName.Concept, SyncEntityName.ProgramConfig, SyncEntityName.Video,
                SyncEntityName.SubjectType, SyncEntityName.ChecklistItemDetail, SyncEntityName.FormElementGroup, SyncEntityName.FormElement, SyncEntityName.ConceptAnswer,
                SyncEntityName.IdentifierSource, SyncEntityName.OrganisationConfig, SyncEntityName.PlatformTranslation, SyncEntityName.Translation, SyncEntityName.Groups,
                SyncEntityName.MyGroups, SyncEntityName.GroupPrivileges, SyncEntityName.Extension, SyncEntityName.GroupRole, SyncEntityName.LocationHierarchy, SyncEntityName.ReportCard,
                SyncEntityName.Dashboard, SyncEntityName.DashboardSection, SyncEntityName.DashboardFilter, SyncEntityName.DashboardSectionCardMapping, SyncEntityName.ApprovalStatus, SyncEntityName.GroupDashboard,
                SyncEntityName.News, SyncEntityName.UserInfo, SyncEntityName.Privilege, SyncEntityName.StandardReportCardType, SyncEntityName.Documentation, SyncEntityName.DocumentationItem,
                SyncEntityName.Task, SyncEntityName.TaskType, SyncEntityName.TaskStatus, SyncEntityName.TaskUnAssignment, SyncEntityName.UserSubjectAssignment, SyncEntityName.LocationMapping
        ));

        if (!scopeAwareEAS) addToSyncableItems(syncableItems, Collections.singletonList(SyncEntityName.EntityApprovalStatus));

        return syncableItems;
    }

    private boolean addToSyncableItems(HashSet<SyncableItem> syncableItems, SyncEntityName syncEntityName, String uuid) {
        return syncableItems.add(new SyncableItem(syncEntityName, uuid));
    }

    private void addToSyncableItems(HashSet<SyncableItem> syncableItems, List<SyncEntityName> entityNames) {
        entityNames.forEach(entityName -> syncableItems.add(new SyncableItem(entityName, "")));
    }
}
