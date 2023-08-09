package org.avni.server.web;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.SyncableItem;
import org.avni.server.service.*;
import org.avni.server.service.accessControl.GroupPrivilegeService;
import org.avni.server.service.accessControl.PrivilegeService;
import org.avni.server.service.application.MenuItemService;
import org.avni.server.web.request.EntitySyncStatusContract;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class SyncController {

    private static final Map<String, Integer> nowMap = new HashMap<String, Integer>() {{
        put("live", 10);
    }};

    private final Environment environment;
    private final Map<String, ScopeAwareService> scopeAwareServiceMap = new HashMap<>();
    private final Map<String, NonScopeAwareService> nonScopeAwareServiceMap = new HashMap<>();
    private final IndividualService individualService;
    private final EncounterService encounterService;
    private final ProgramEnrolmentService programEnrolmentService;
    private final ProgramEncounterService programEncounterService;
    private final ChecklistService checklistService;
    private final ChecklistItemService checklistItemService;
    private final IndividualRelationshipService individualRelationshipService;
    private final GroupSubjectService groupSubjectService;
    private final CommentService commentService;
    private final CommentThreadService commentThreadService;
    private final LocationService addressLevelService;
    private final LocationMappingService locationMappingService;
    private final IdentifierAssignmentService identifierAssignmentService;
    private final ChecklistDetailService checklistDetailService;
    private final RuleService ruleService;
    private final RuleDependencyService ruleDependencyService;
    private final FormService formService;
    private final FormMappingService formMappingService;
    private final EncounterTypeService encounterTypeService;
    private final ProgramService programService;
    private final ProgramOutcomeService programOutcomeService;
    private final GenderService genderService;
    private final IndividualRelationService individualRelationService;
    private final IndividualRelationGenderMappingService individualRelationGenderMappingService;
    private final IndividualRelationshipTypeService individualRelationshipTypeService;
    private final ConceptService conceptService;
    private final ProgramConfigService programConfigService;
    private final VideoService videoService;
    private final SubjectTypeService subjectTypeService;
    private final ChecklistItemDetailService checklistItemDetailService;
    private final FormElementGroupService formElementGroupService;
    private final FormElementService formElementService;
    private final ConceptAnswerService conceptAnswerService;
    private final IdentifierSourceService identifierSourceService;
    private final OrganisationConfigService organisationConfigService;
    private final PlatformTranslationService platformTranslationService;
    private final TranslationService translationService;
    private final GroupsService groupsService;
    private final GroupPrivilegeService groupPrivilegeService;
    private final GroupRoleService groupRoleService;
    private final CardService cardService;
    private final DashboardService dashboardService;
    private final DashboardSectionService dashboardSectionService;
    private final DashboardSectionCardMappingService dashboardSectionCardMappingService;
    private final ApprovalStatusService approvalStatusService;
    private final GroupDashboardService groupDashboardService;
    private final EntityApprovalStatusService entityApprovalStatusService;
    private final NewsService newsService;
    private final UserService userService;
    private final PrivilegeService privilegeService;
    private final StandardReportCardTypeService standardReportCardTypeService;
    private final UserGroupService userGroupService;
    private final LocationHierarchyService locationHierarchyService;
    private final ExtensionService extensionService;
    private final SubjectMigrationService subjectMigrationService;
    private final SyncDetailsService syncDetailService;
    private final Logger logger;
    private final DocumentationService documentationService;
    private final DocumentationItemService documentationItemService;
    private final TaskService taskService;
    private final TaskTypeService taskTypeService;
    private final TaskStatusService taskStatusService;
    private final TaskUnAssigmentService taskUnAssigmentService;
    private final SubjectProgramEligibilityService subjectProgramEligibilityService;
    private final MenuItemService menuItemService;
    private final UserSubjectAssignmentService userSubjectAssignmentService;
    private final ScopedEntityApprovalStatusService scopedEntityApprovalStatusService;
    private final DashboardFilterService dashboardFilterService;

    @Autowired
    public SyncController(Environment environment, IndividualService individualService, EncounterService encounterService,
                          ProgramEnrolmentService programEnrolmentService, ProgramEncounterService programEncounterService,
                          ChecklistService checklistService, ChecklistItemService checklistItemService,
                          IndividualRelationshipService individualRelationshipService,
                          GroupSubjectService groupSubjectService, CommentService commentService,
                          CommentThreadService commentThreadService, LocationService addressLevelService,
                          LocationMappingService locationMappingService, IdentifierAssignmentService identifierAssignmentService, ChecklistDetailService checklistDetailService,
                          RuleService ruleService, RuleDependencyService ruleDependencyService, FormService formService,
                          FormMappingService formMappingService, EncounterTypeService encounterTypeService,
                          ProgramService programService, ProgramOutcomeService programOutcomeService,
                          GenderService genderService, IndividualRelationService individualRelationService,
                          IndividualRelationGenderMappingService individualRelationGenderMappingService,
                          IndividualRelationshipTypeService individualRelationshipTypeService, ConceptService conceptService,
                          ProgramConfigService programConfigService, VideoService videoService,
                          SubjectTypeService subjectTypeService, ChecklistItemDetailService checklistItemDetailService,
                          FormElementGroupService formElementGroupService, FormElementService formElementService,
                          ConceptAnswerService conceptAnswerService, IdentifierSourceService identifierSourceService,
                          OrganisationConfigService organisationConfigService,
                          PlatformTranslationService platformTranslationService, TranslationService translationService,
                          GroupsService groupsService, GroupPrivilegeService groupPrivilegeService,
                          GroupRoleService groupRoleService, CardService cardService, DashboardService dashboardService,
                          DashboardSectionService dashboardSectionService, DashboardSectionCardMappingService dashboardSectionCardMappingService, ApprovalStatusService approvalStatusService, GroupDashboardService groupDashboardService,
                          EntityApprovalStatusService entityApprovalStatusService, NewsService newsService,
                          UserService userService, PrivilegeService privilegeService,
                          StandardReportCardTypeService standardReportCardTypeService, UserGroupService userGroupService,
                          LocationHierarchyService locationHierarchyService, ExtensionService extensionService,
                          SubjectMigrationService subjectMigrationService, SyncDetailsService syncDetailService,
                          DocumentationService documentationService, DocumentationItemService documentationItemService,
                          TaskService taskService, TaskTypeService taskTypeService, TaskStatusService taskStatusService,
                          TaskUnAssigmentService taskUnAssigmentService, SubjectProgramEligibilityService subjectProgramEligibilityService, MenuItemService menuItemService, UserSubjectAssignmentService userSubjectAssignmentService,
                          ScopedEntityApprovalStatusService scopedEntityApprovalStatusService, DashboardFilterService dashboardFilterService) {
        this.environment = environment;
        this.individualService = individualService;
        this.encounterService = encounterService;
        this.programEnrolmentService = programEnrolmentService;
        this.programEncounterService = programEncounterService;
        this.checklistService = checklistService;
        this.checklistItemService = checklistItemService;
        this.individualRelationshipService = individualRelationshipService;
        this.groupSubjectService = groupSubjectService;
        this.commentService = commentService;
        this.commentThreadService = commentThreadService;
        this.addressLevelService = addressLevelService;
        this.locationMappingService = locationMappingService;
        this.identifierAssignmentService = identifierAssignmentService;
        this.checklistDetailService = checklistDetailService;
        this.ruleService = ruleService;
        this.ruleDependencyService = ruleDependencyService;
        this.formService = formService;
        this.formMappingService = formMappingService;
        this.encounterTypeService = encounterTypeService;
        this.programService = programService;
        this.programOutcomeService = programOutcomeService;
        this.genderService = genderService;
        this.individualRelationService = individualRelationService;
        this.individualRelationGenderMappingService = individualRelationGenderMappingService;
        this.individualRelationshipTypeService = individualRelationshipTypeService;
        this.conceptService = conceptService;
        this.programConfigService = programConfigService;
        this.videoService = videoService;
        this.subjectTypeService = subjectTypeService;
        this.checklistItemDetailService = checklistItemDetailService;
        this.formElementGroupService = formElementGroupService;
        this.formElementService = formElementService;
        this.conceptAnswerService = conceptAnswerService;
        this.identifierSourceService = identifierSourceService;
        this.organisationConfigService = organisationConfigService;
        this.platformTranslationService = platformTranslationService;
        this.translationService = translationService;
        this.groupsService = groupsService;
        this.groupPrivilegeService = groupPrivilegeService;
        this.groupRoleService = groupRoleService;
        this.cardService = cardService;
        this.dashboardService = dashboardService;
        this.dashboardSectionService = dashboardSectionService;
        this.dashboardSectionCardMappingService = dashboardSectionCardMappingService;
        this.approvalStatusService = approvalStatusService;
        this.groupDashboardService = groupDashboardService;
        this.entityApprovalStatusService = entityApprovalStatusService;
        this.newsService = newsService;
        this.userService = userService;
        this.privilegeService = privilegeService;
        this.standardReportCardTypeService = standardReportCardTypeService;
        this.userGroupService = userGroupService;
        this.locationHierarchyService = locationHierarchyService;
        this.extensionService = extensionService;
        this.subjectMigrationService = subjectMigrationService;
        this.syncDetailService = syncDetailService;
        this.documentationService = documentationService;
        this.documentationItemService = documentationItemService;
        this.taskService = taskService;
        this.taskTypeService = taskTypeService;
        this.taskStatusService = taskStatusService;
        this.taskUnAssigmentService = taskUnAssigmentService;
        this.subjectProgramEligibilityService = subjectProgramEligibilityService;
        this.menuItemService = menuItemService;
        this.userSubjectAssignmentService = userSubjectAssignmentService;
        this.scopedEntityApprovalStatusService = scopedEntityApprovalStatusService;
        this.dashboardFilterService = dashboardFilterService;
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    @PostConstruct
    public void init() {
        populateScopeAwareRepositoryMap();
        populateEntityNameToTableMap();
    }

    private void populateScopeAwareRepositoryMap() {
        scopeAwareServiceMap.put("Individual", individualService);
        scopeAwareServiceMap.put("Encounter", encounterService);
        scopeAwareServiceMap.put("ProgramEnrolment", programEnrolmentService);
        scopeAwareServiceMap.put("ProgramEncounter", programEncounterService);
        scopeAwareServiceMap.put("Checklist", checklistService);
        scopeAwareServiceMap.put("ChecklistItem", checklistItemService);
        scopeAwareServiceMap.put("IndividualRelationship", individualRelationshipService);
        scopeAwareServiceMap.put("GroupSubject", groupSubjectService);
        scopeAwareServiceMap.put("Comment", commentService);
        scopeAwareServiceMap.put("CommentThread", commentThreadService);
        scopeAwareServiceMap.put("AddressLevel", addressLevelService);
        scopeAwareServiceMap.put("LocationMapping", locationMappingService);
        scopeAwareServiceMap.put("SubjectMigration", subjectMigrationService);
        scopeAwareServiceMap.put("SubjectProgramEligibility", subjectProgramEligibilityService);
        scopeAwareServiceMap.put("SubjectEntityApprovalStatus", scopedEntityApprovalStatusService);
        scopeAwareServiceMap.put("EncounterEntityApprovalStatus", scopedEntityApprovalStatusService);
        scopeAwareServiceMap.put("ProgramEncounterEntityApprovalStatus", scopedEntityApprovalStatusService);
        scopeAwareServiceMap.put("ProgramEnrolmentEntityApprovalStatus", scopedEntityApprovalStatusService);
        scopeAwareServiceMap.put("ChecklistItemEntityApprovalStatus", scopedEntityApprovalStatusService);
    }

    private void populateEntityNameToTableMap() {
        nonScopeAwareServiceMap.put("IdentifierAssignment", identifierAssignmentService);
        nonScopeAwareServiceMap.put("ChecklistDetail", checklistDetailService);
        nonScopeAwareServiceMap.put("Rule", ruleService);
        nonScopeAwareServiceMap.put("RuleDependency", ruleDependencyService);
        nonScopeAwareServiceMap.put("Form", formService);
        nonScopeAwareServiceMap.put("FormMapping", formMappingService);
        nonScopeAwareServiceMap.put("EncounterType", encounterTypeService);
        nonScopeAwareServiceMap.put("Program", programService);
        nonScopeAwareServiceMap.put("ProgramOutcome", programOutcomeService);
        nonScopeAwareServiceMap.put("Gender", genderService);
        nonScopeAwareServiceMap.put("IndividualRelation", individualRelationService);
        nonScopeAwareServiceMap.put("IndividualRelationGenderMapping", individualRelationGenderMappingService);
        nonScopeAwareServiceMap.put("IndividualRelationshipType", individualRelationshipTypeService);
        nonScopeAwareServiceMap.put("Concept", conceptService);
        nonScopeAwareServiceMap.put("ProgramConfig", programConfigService);
        nonScopeAwareServiceMap.put("Video", videoService);
        nonScopeAwareServiceMap.put("SubjectType", subjectTypeService);
        nonScopeAwareServiceMap.put("ChecklistItemDetail", checklistItemDetailService);
        nonScopeAwareServiceMap.put("FormElementGroup", formElementGroupService);
        nonScopeAwareServiceMap.put("FormElement", formElementService);
        nonScopeAwareServiceMap.put("ConceptAnswer", conceptAnswerService);
        nonScopeAwareServiceMap.put("IdentifierSource", identifierSourceService);
        nonScopeAwareServiceMap.put("OrganisationConfig", organisationConfigService);
        nonScopeAwareServiceMap.put("PlatformTranslation", platformTranslationService);
        nonScopeAwareServiceMap.put("Translation", translationService);
        nonScopeAwareServiceMap.put("Groups", groupsService);
        nonScopeAwareServiceMap.put("MyGroups", userGroupService);
        nonScopeAwareServiceMap.put("GroupPrivileges", groupPrivilegeService);
        nonScopeAwareServiceMap.put("Extension", extensionService);
        nonScopeAwareServiceMap.put("GroupRole", groupRoleService);
        nonScopeAwareServiceMap.put("LocationHierarchy", locationHierarchyService);
        nonScopeAwareServiceMap.put("ReportCard", cardService);
        nonScopeAwareServiceMap.put("Dashboard", dashboardService);
        nonScopeAwareServiceMap.put("DashboardSection", dashboardSectionService);
        nonScopeAwareServiceMap.put("DashboardFilter", dashboardFilterService);
        nonScopeAwareServiceMap.put("DashboardSectionCardMapping", dashboardSectionCardMappingService);
        nonScopeAwareServiceMap.put("ApprovalStatus", approvalStatusService);
        nonScopeAwareServiceMap.put("GroupDashboard", groupDashboardService);
        nonScopeAwareServiceMap.put("EntityApprovalStatus", entityApprovalStatusService);
        nonScopeAwareServiceMap.put("News", newsService);
        nonScopeAwareServiceMap.put("Documentation", documentationService);
        nonScopeAwareServiceMap.put("DocumentationItem", documentationItemService);
        nonScopeAwareServiceMap.put("UserInfo", userService);
        nonScopeAwareServiceMap.put("Privilege", privilegeService);
        nonScopeAwareServiceMap.put("StandardReportCardType", standardReportCardTypeService);
        nonScopeAwareServiceMap.put("Task", taskService);
        nonScopeAwareServiceMap.put("TaskType", taskTypeService);
        nonScopeAwareServiceMap.put("TaskStatus", taskStatusService);
        nonScopeAwareServiceMap.put("TaskUnAssignment", taskUnAssigmentService);
        nonScopeAwareServiceMap.put("MenuItem", menuItemService);
        nonScopeAwareServiceMap.put("UserSubjectAssignment", userSubjectAssignmentService);
    }

    /**
     *
     * @param entitySyncStatusContracts : This would contain all entries from EntitySyncStatus table maintained on avni-client,
     *                                 which get populated based on all type of entities synced previously
     * @param isStockApp
     * @return
     */
    @PostMapping(value = "/v2/syncDetails")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<?> getSyncDetailsWithScopeAwareEAS(@RequestBody List<EntitySyncStatusContract> entitySyncStatusContracts,
                                            @RequestParam(value = "isStockApp", required = false) boolean isStockApp) {
        DateTime now = new DateTime();
        DateTime nowMinus10Seconds = getNowMinus10Seconds();
        Set<SyncableItem> allSyncableItems = syncDetailService.getAllSyncableItems(true);
        long afterSyncDetailsService = new DateTime().getMillis();
        logger.info(String.format("Time taken for syncDetailsService %d", afterSyncDetailsService - now.getMillis()));
        List<EntitySyncStatusContract> changedEntities = getChangedEntities(entitySyncStatusContracts, allSyncableItems, true);
        logger.info(String.format("Time taken for stuff %d", new DateTime().getMillis() - afterSyncDetailsService));
        return ResponseEntity.ok().body(new JsonObject()
                .with("syncDetails", changedEntities)
                .with("now", now)
                .with("nowMinus10Seconds", nowMinus10Seconds)
        );
    }

    /**
     *
     * @param entitySyncStatusContracts : This would contain all entries from EntitySyncStatus table maintained on avni-client,
     *                                  which get populated based on all type of entities synced previously
     * @param isStockApp
     * @return
     */
    @PostMapping(value = "/syncDetails")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<?> getSyncDetails(@RequestBody List<EntitySyncStatusContract> entitySyncStatusContracts,
                                            @RequestParam(value = "isStockApp", required = false) boolean isStockApp) {
        DateTime now = new DateTime();
        DateTime nowMinus10Seconds = getNowMinus10Seconds();
        Set<SyncableItem> allSyncableItems = syncDetailService.getAllSyncableItems(false);
        long afterSyncDetailsService = new DateTime().getMillis();
        logger.info(String.format("Time taken for syncDetailsService %d", afterSyncDetailsService - now.getMillis()));
        List<EntitySyncStatusContract> changedEntities = getChangedEntities(entitySyncStatusContracts, allSyncableItems, false);
        logger.info(String.format("Time taken for stuff %d", new DateTime().getMillis() - afterSyncDetailsService));
        return ResponseEntity.ok().body(new JsonObject()
                .with("syncDetails", changedEntities)
                .with("now", now)
                .with("nowMinus10Seconds", nowMinus10Seconds)
        );
    }

    private List<EntitySyncStatusContract> getChangedEntities(List<EntitySyncStatusContract> entitySyncStatusContracts, Set<SyncableItem> allSyncableItems, boolean scopeAwareEAS) {
        allSyncableItems.forEach(syncableItem -> {
            if (entitySyncStatusContracts.stream().noneMatch(entitySyncStatusContract ->
                    entitySyncStatusContract.matchesEntity(syncableItem))) {
                entitySyncStatusContracts.add(EntitySyncStatusContract.create(syncableItem.getSyncEntityName().name(), syncableItem.getEntityTypeUuid()));
            }
        });
        removeDisabledEntities(entitySyncStatusContracts, allSyncableItems);

        return entitySyncStatusContracts.stream()
                .filter((entitySyncStatusContract) -> filterChangedEntities(entitySyncStatusContract, scopeAwareEAS))
                .collect(Collectors.toList());
    }

    /**
     * The purpose of this method is to iterate on {@code entitySyncStatusContracts}(1st param) and removing from it entries corresponding
     * to entities with
     * <ul>
     *     <li>
     *         entityName equal to [Sub-types of EntityApprovalStatus] OR [GeneralEncounter, ProgramEncounter and ProgramEnrolments]
     *     </li>
     *     <b>and</b>
     *     <li>
     *         that are not present in {@code allSyncableItems}(2nd param)
     *     </li>
     * </ul>
     * <br/>
     * <br/>
     * This is done in-order to avoid client from attempting to sync the following due to below listed reasons
     *  <ol>
     *      <li>
     *          [Sub-types of EntityApprovalStatus] : The data is synced as part of EntityApprovalStatus sync itself,
     *                                                as we do not separately store them as different subTypes in backend-server-database
     *      </li>
     *      <li>
     *          [GeneralEncounter, ProgramEncounter and ProgramEnrolments] : Few entries of these types would be missing in {@code allSyncableItems}(2nd param)
     *                                             as their FormMappings could have been voided at a later stage and aren't eligible for sync anymore
     *      </li>
     *  </ol>
     *  <p>
     * @param entitySyncStatusContracts List of EntitySyncStatusContracts obtained after merging,
     *                                 the avni-client specified entitySyncStatusContracts with {@code allSyncableItems}(2nd param)
     * @param allSyncableItems List of EntitySyncStatusContract generated on server based on the status of latest FormMapping configurations and entity-relationships
     */
    private void removeDisabledEntities(List<EntitySyncStatusContract> entitySyncStatusContracts, Set<SyncableItem> allSyncableItems) {
        entitySyncStatusContracts.removeIf(entitySyncStatusContract -> entitySyncStatusContract.mightHaveToBeIgnoredDuringSync() &&
                allSyncableItems.stream().noneMatch(entitySyncStatusContract::matchesEntity));
    }

    private boolean filterChangedEntities(EntitySyncStatusContract entitySyncStatusContract, boolean scopeAwareEAS) {
        String entityName = entitySyncStatusContract.getEntityName();
        DateTime loadedSince = entitySyncStatusContract.getLoadedSince();
        if(scopeAwareEAS) nonScopeAwareServiceMap.remove("EntityApprovalStatus");
        ScopeAwareService scopeAwareService = this.scopeAwareServiceMap.get(entityName);
        NonScopeAwareService nonScopeAwareService = this.nonScopeAwareServiceMap.get(entityName);

        if (nonScopeAwareService != null) {
            return nonScopeAwareService.isNonScopeEntityChanged(loadedSince);
        }

        if (scopeAwareService != null) {
            return scopeAwareService.isScopeEntityChanged(loadedSince, entitySyncStatusContract.getEntityTypeUuid());
        }

        return false;
    }

    /**
     * This is a hack to fix the problem of missing data when multiple users sync at the same time.
     * During sync, it is possible that the tables being sync GETted are also being updated concurrently.
     * <p>
     * By retrieving data that is slightly old, we ensure that any data that was updated during the sync
     * is retrieved completely during the next sync process, and we do not miss any data.
     */
    private DateTime getNowMinus10Seconds() {
        return new DateTime().minusSeconds(nowMap.getOrDefault(environment.getActiveProfiles()[0], 0));
    }
}
