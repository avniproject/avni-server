package org.avni.server.service;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import org.apache.commons.io.IOUtils;
import org.avni.messaging.contract.MessageRuleContract;
import org.avni.messaging.repository.ManualMessageRepository;
import org.avni.messaging.repository.MessageReceiverRepository;
import org.avni.messaging.repository.MessageRequestQueueRepository;
import org.avni.messaging.repository.MessageRuleRepository;
import org.avni.messaging.service.MessagingService;
import org.avni.server.application.Form;
import org.avni.server.application.FormMapping;
import org.avni.server.dao.*;
import org.avni.server.dao.application.*;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.dao.individualRelationship.*;
import org.avni.server.dao.program.SubjectProgramEligibilityRepository;
import org.avni.server.dao.task.TaskRepository;
import org.avni.server.dao.task.TaskStatusRepository;
import org.avni.server.dao.task.TaskTypeRepository;
import org.avni.server.dao.task.TaskUnAssignmentRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.importer.batch.model.BundleFolder;
import org.avni.server.mapper.dashboard.DashboardMapper;
import org.avni.server.mapper.dashboard.ReportCardMapper;
import org.avni.server.service.application.MenuItemService;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.util.S;
import org.avni.server.util.S3File;
import org.avni.server.web.contract.GroupDashboardBundleContract;
import org.avni.server.web.contract.reports.DashboardBundleContract;
import org.avni.server.web.request.*;
import org.avni.server.web.request.application.ChecklistDetailRequest;
import org.avni.server.web.request.application.FormContract;
import org.avni.server.web.request.application.menu.MenuItemContract;
import org.avni.server.web.request.webapp.CatchmentExport;
import org.avni.server.web.request.webapp.CatchmentsExport;
import org.avni.server.web.request.webapp.ConceptExport;
import org.avni.server.web.request.webapp.documentation.DocumentationContract;
import org.avni.server.web.request.webapp.task.TaskStatusContract;
import org.avni.server.web.request.webapp.task.TaskTypeContract;
import org.avni.server.web.response.reports.ReportCardBundleContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class OrganisationService {
    private final FormRepository formRepository;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final LocationRepository locationRepository;
    private final CatchmentRepository catchmentRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final OperationalSubjectTypeRepository operationalSubjectTypeRepository;
    private final OperationalEncounterTypeRepository operationalEncounterTypeRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final ProgramRepository programRepository;
    private final OperationalProgramRepository operationalProgramRepository;
    private final FormMappingRepository formMappingRepository;
    private final OrganisationConfigRepository organisationConfigRepository;
    private final IdentifierSourceRepository identifierSourceRepository;
    private final ConceptRepository conceptRepository;
    private final IndividualRelationService individualRelationService;
    private final IndividualRelationshipTypeService individualRelationshipTypeService;
    private final ChecklistDetailService checklistDetailService;
    private final GroupRepository groupRepository;
    private final GroupRoleRepository groupRoleRepository;
    private final GroupPrivilegeRepository groupPrivilegeRepository;
    private final UserGroupRepository userGroupRepository;
    private final ChecklistItemDetailRepository checklistItemDetailRepository;
    private final ChecklistDetailRepository checklistDetailRepository;
    private final IdentifierUserAssignmentRepository identifierUserAssignmentRepository;
    private final IndividualRelationGenderMappingRepository individualRelationGenderMappingRepository;
    private final IndividualRelationshipTypeRepository individualRelationshipTypeRepository;
    private final IndividualRelationRepository individualRelationRepository;
    private final FormElementRepository formElementRepository;
    private final FormElementGroupRepository formElementGroupRepository;
    private final ConceptAnswerRepository conceptAnswerRepository;
    private final TranslationRepository translationRepository;
    private final VideoRepository videoRepository;
    private final VideoService videoService;
    private final CardService cardService;
    private final DashboardFilterRepository dashboardFilterRepository;
    private final MenuItemService menuItemService;

    private final MessagingService messagingService;
    private final CardRepository cardRepository;
    private final DashboardRepository dashboardRepository;
    private final DashboardSectionCardMappingRepository dashboardSectionCardMappingRepository;
    private final DashboardSectionRepository dashboardSectionRepository;
    private final GroupDashboardRepository groupDashboardRepository;
    private final DashboardService dashboardService;
    private final Msg91ConfigRepository msg91ConfigRepository;
    private final S3Service s3Service;
    private final UserService userService;

    //Tx repositories
    private final RuleFailureTelemetryRepository ruleFailureTelemetryRepository;
    private final IdentifierAssignmentRepository identifierAssignmentRepository;
    private final SyncTelemetryRepository syncTelemetryRepository;
    private final VideoTelemetricRepository videoTelemetricRepository;
    private final GroupSubjectRepository groupSubjectRepository;
    private final IndividualRelationshipRepository individualRelationshipRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistRepository checklistRepository;
    private final ProgramEncounterRepository programEncounterRepository;
    private final ProgramEnrolmentRepository programEnrolmentRepository;
    private final EncounterRepository encounterRepository;
    private final IndividualRepository individualRepository;
    private final EntityApprovalStatusRepository entityApprovalStatusRepository;
    private final CommentRepository commentRepository;
    private final CommentThreadRepository commentThreadRepository;
    private final NewsRepository newsRepository;
    private final SubjectMigrationRepository subjectMigrationRepository;
    private final DocumentationService documentationService;
    private final TaskTypeService taskTypeService;
    private final TaskStatusService taskStatusService;
    private final EntityTypeRetrieverService entityTypeRetrieverService;
    private final RuleDependencyRepository ruleDependencyRepository;
    private final RuleRepository ruleRepository;
    private final UserSubjectAssignmentRepository userSubjectAssignmentRepository;
    private final SubjectProgramEligibilityRepository subjectProgramEligibilityRepository;
    private final TaskRepository taskRepository;
    private final MessageRequestQueueRepository messageRequestQueueRepository;
    private final MessageReceiverRepository messageReceiverRepository;
    private final OrganisationConfigService organisationConfigService;
    private final GenderRepository genderRepository;
    private final OrganisationRepository organisationRepository;
    private final ReportCardMapper reportCardMapper;
    private final UserSubjectRepository userSubjectRepository;
    private final Logger logger;
    private final AnswerConceptMigrationRepository answerConceptMigrationRepository;
    private final CustomQueryRepository customQueryRepository;
    private final DocumentationRepository documentationRepository;
    private final DocumentationItemRepository documentationItemRepository;
    private final ExportJobParametersRepository exportJobParametersRepository;
    private final ExternalSystemConfigRepository externalSystemConfigRepository;
    private final LocationMappingRepository locationMappingRepository;
    private final MenuItemRepository menuItemRepository;
    private final ManualMessageRepository manualMessageRepository;
    private final MessageRuleRepository messageRuleRepository;
    private final ResetSyncRepository resetSyncRepository;
    private final RuleFailureLogRepository ruleFailureLogRepository;
    private final TaskStatusRepository taskStatusRepository;
    private final TaskTypeRepository taskTypeRepository;
    private final TaskUnAssignmentRepository taskUnAssignmentRepository;
    private final UserRepository userRepository;

    private final JdbcTemplate jdbcTemplate;
    private final DashboardMapper dashboardMapper;
    private final GroupDashboardService groupDashboardService;
    private final CustomQueryService customQueryService;
    private final StorageManagementConfigRepository storageManagementConfigRepository;

    @Autowired
    public OrganisationService(FormRepository formRepository,
                               AddressLevelTypeRepository addressLevelTypeRepository,
                               LocationRepository locationRepository,
                               CatchmentRepository catchmentRepository,
                               SubjectTypeRepository subjectTypeRepository,
                               OperationalSubjectTypeRepository operationalSubjectTypeRepository,
                               OperationalEncounterTypeRepository operationalEncounterTypeRepository,
                               EncounterTypeRepository encounterTypeRepository,
                               ProgramRepository programRepository,
                               OperationalProgramRepository operationalProgramRepository,
                               FormMappingRepository formMappingRepository,
                               OrganisationConfigRepository organisationConfigRepository,
                               IdentifierSourceRepository identifierSourceRepository,
                               ConceptRepository conceptRepository,
                               IndividualRelationService individualRelationService,
                               IndividualRelationshipTypeService individualRelationshipTypeService,
                               ChecklistDetailService checklistDetailService,
                               GroupRepository groupRepository,
                               GroupRoleRepository groupRoleRepository,
                               GroupPrivilegeRepository groupPrivilegeRepository,
                               UserGroupRepository userGroupRepository,
                               ChecklistItemDetailRepository checklistItemDetailRepository,
                               ChecklistDetailRepository checklistDetailRepository,
                               IdentifierUserAssignmentRepository identifierUserAssignmentRepository,
                               IndividualRelationGenderMappingRepository individualRelationGenderMappingRepository,
                               IndividualRelationshipTypeRepository individualRelationshipTypeRepository,
                               IndividualRelationRepository individualRelationRepository,
                               FormElementRepository formElementRepository,
                               FormElementGroupRepository formElementGroupRepository,
                               ConceptAnswerRepository conceptAnswerRepository,
                               TranslationRepository translationRepository,
                               VideoRepository videoRepository,
                               VideoService videoService,
                               CardService cardService,
                               DashboardService dashboardService,
                               MenuItemService menuItemService,
                               MessagingService messagingService,
                               CardRepository cardRepository,
                               DashboardRepository dashboardRepository,
                               DashboardSectionCardMappingRepository dashboardSectionCardMappingRepository,
                               DashboardFilterRepository dashboardFilterRepository, DashboardSectionRepository dashboardSectionRepository,
                               GroupDashboardRepository groupDashboardRepository,
                               Msg91ConfigRepository msg91ConfigRepository,
                               S3Service s3Service, UserService userService, RuleFailureTelemetryRepository ruleFailureTelemetryRepository,
                               IdentifierAssignmentRepository identifierAssignmentRepository,
                               SyncTelemetryRepository syncTelemetryRepository,
                               VideoTelemetricRepository videoTelemetricRepository,
                               GroupSubjectRepository groupSubjectRepository,
                               IndividualRelationshipRepository individualRelationshipRepository,
                               ChecklistItemRepository checklistItemRepository,
                               ChecklistRepository checklistRepository,
                               ProgramEncounterRepository programEncounterRepository,
                               ProgramEnrolmentRepository programEnrolmentRepository,
                               EncounterRepository encounterRepository,
                               IndividualRepository individualRepository,
                               EntityApprovalStatusRepository entityApprovalStatusRepository,
                               CommentRepository commentRepository,
                               CommentThreadRepository commentThreadRepository,
                               NewsRepository newsRepository,
                               SubjectMigrationRepository subjectMigrationRepository,
                               DocumentationService documentationService,
                               TaskTypeService taskTypeService,
                               TaskStatusService taskStatusService,
                               EntityTypeRetrieverService entityTypeRetrieverService,
                               RuleDependencyRepository ruleDependencyRepository,
                               RuleRepository ruleRepository,
                               UserSubjectAssignmentRepository userSubjectAssignmentRepository,
                               SubjectProgramEligibilityRepository subjectProgramEligibilityRepository,
                               TaskRepository taskRepository,
                               MessageRequestQueueRepository messageRequestQueueRepository,
                               MessageReceiverRepository messageReceiverRepository,
                               OrganisationConfigService organisationConfigService,
                               GenderRepository genderRepository,
                               OrganisationRepository organisationRepository,
                               UserSubjectRepository userSubjectRepository,
                               AnswerConceptMigrationRepository answerConceptMigrationRepository,
                               CustomQueryRepository customQueryRepository,
                               DocumentationRepository documentationRepository,
                               DocumentationItemRepository documentationItemRepository,
                               ExportJobParametersRepository exportJobParametersRepository,
                               ExternalSystemConfigRepository externalSystemConfigRepository,
                               LocationMappingRepository locationMappingRepository,
                               MenuItemRepository menuItemRepository,
                               ManualMessageRepository manualMessageRepository,
                               MessageRuleRepository messageRuleRepository,
                               ResetSyncRepository resetSyncRepository,
                               RuleFailureLogRepository ruleFailureLogRepository,
                               TaskStatusRepository taskStatusRepository,
                               TaskTypeRepository taskTypeRepository,
                               TaskUnAssignmentRepository taskUnAssignmentRepository,
                               UserRepository userRepository,
                               JdbcTemplate jdbcTemplate,
                               ReportCardMapper reportCardMapper,
                               DashboardMapper dashboardMapper,
                               GroupDashboardService groupDashboardService, CustomQueryService customQueryService, StorageManagementConfigRepository storageManagementConfigRepository) {
        this.formRepository = formRepository;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.locationRepository = locationRepository;
        this.catchmentRepository = catchmentRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.operationalSubjectTypeRepository = operationalSubjectTypeRepository;
        this.operationalEncounterTypeRepository = operationalEncounterTypeRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.programRepository = programRepository;
        this.operationalProgramRepository = operationalProgramRepository;
        this.formMappingRepository = formMappingRepository;
        this.organisationConfigRepository = organisationConfigRepository;
        this.identifierSourceRepository = identifierSourceRepository;
        this.conceptRepository = conceptRepository;
        this.individualRelationService = individualRelationService;
        this.individualRelationshipTypeService = individualRelationshipTypeService;
        this.checklistDetailService = checklistDetailService;
        this.groupRepository = groupRepository;
        this.groupRoleRepository = groupRoleRepository;
        this.groupPrivilegeRepository = groupPrivilegeRepository;
        this.userGroupRepository = userGroupRepository;
        this.checklistItemDetailRepository = checklistItemDetailRepository;
        this.checklistDetailRepository = checklistDetailRepository;
        this.identifierUserAssignmentRepository = identifierUserAssignmentRepository;
        this.individualRelationGenderMappingRepository = individualRelationGenderMappingRepository;
        this.individualRelationshipTypeRepository = individualRelationshipTypeRepository;
        this.individualRelationRepository = individualRelationRepository;
        this.formElementRepository = formElementRepository;
        this.formElementGroupRepository = formElementGroupRepository;
        this.conceptAnswerRepository = conceptAnswerRepository;
        this.translationRepository = translationRepository;
        this.videoRepository = videoRepository;
        this.videoService = videoService;
        this.cardService = cardService;
        this.menuItemService = menuItemService;
        this.messagingService = messagingService;
        this.cardRepository = cardRepository;
        this.dashboardRepository = dashboardRepository;
        this.dashboardSectionCardMappingRepository = dashboardSectionCardMappingRepository;
        this.dashboardFilterRepository = dashboardFilterRepository;
        this.dashboardSectionRepository = dashboardSectionRepository;
        this.groupDashboardRepository = groupDashboardRepository;
        this.msg91ConfigRepository = msg91ConfigRepository;
        this.s3Service = s3Service;
        this.userService = userService;
        this.ruleFailureTelemetryRepository = ruleFailureTelemetryRepository;
        this.identifierAssignmentRepository = identifierAssignmentRepository;
        this.syncTelemetryRepository = syncTelemetryRepository;
        this.videoTelemetricRepository = videoTelemetricRepository;
        this.groupSubjectRepository = groupSubjectRepository;
        this.individualRelationshipRepository = individualRelationshipRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.checklistRepository = checklistRepository;
        this.programEncounterRepository = programEncounterRepository;
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.encounterRepository = encounterRepository;
        this.individualRepository = individualRepository;
        this.entityApprovalStatusRepository = entityApprovalStatusRepository;
        this.commentRepository = commentRepository;
        this.commentThreadRepository = commentThreadRepository;
        this.newsRepository = newsRepository;
        this.subjectMigrationRepository = subjectMigrationRepository;
        this.documentationService = documentationService;
        this.taskTypeService = taskTypeService;
        this.taskStatusService = taskStatusService;
        this.entityTypeRetrieverService = entityTypeRetrieverService;
        this.ruleDependencyRepository = ruleDependencyRepository;
        this.ruleRepository = ruleRepository;
        this.userSubjectAssignmentRepository = userSubjectAssignmentRepository;
        this.subjectProgramEligibilityRepository = subjectProgramEligibilityRepository;
        this.taskRepository = taskRepository;
        this.messageRequestQueueRepository = messageRequestQueueRepository;
        this.messageReceiverRepository = messageReceiverRepository;
        this.organisationConfigService = organisationConfigService;
        this.genderRepository = genderRepository;
        this.organisationRepository = organisationRepository;
        this.reportCardMapper = reportCardMapper;
        this.dashboardMapper = dashboardMapper;
        this.userSubjectRepository = userSubjectRepository;
        this.answerConceptMigrationRepository = answerConceptMigrationRepository;
        this.customQueryRepository = customQueryRepository;
        this.documentationRepository = documentationRepository;
        this.documentationItemRepository = documentationItemRepository;
        this.exportJobParametersRepository = exportJobParametersRepository;
        this.externalSystemConfigRepository = externalSystemConfigRepository;
        this.locationMappingRepository = locationMappingRepository;
        this.menuItemRepository = menuItemRepository;
        this.manualMessageRepository = manualMessageRepository;
        this.messageRuleRepository = messageRuleRepository;
        this.resetSyncRepository = resetSyncRepository;
        this.ruleFailureLogRepository = ruleFailureLogRepository;
        this.taskStatusRepository = taskStatusRepository;
        this.taskTypeRepository = taskTypeRepository;
        this.taskUnAssignmentRepository = taskUnAssignmentRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.dashboardService = dashboardService;
        this.customQueryService = customQueryService;
        this.storageManagementConfigRepository = storageManagementConfigRepository;
        logger = LoggerFactory.getLogger(this.getClass());
        this.groupDashboardService = groupDashboardService;
    }

    private CrudRepository[] getTxCrudRepositories() {
        CrudRepository[] txCrudRepositories = {
                exportJobParametersRepository,
                manualMessageRepository,
                messageReceiverRepository,
                messageRequestQueueRepository,
                ruleFailureLogRepository,
        };
        return txCrudRepositories;
    }

    private JpaRepository[] getTxJpaRepositories() {
        JpaRepository[] transactionalRepositories = {
                newsRepository,
                commentRepository,
                commentThreadRepository,
                entityApprovalStatusRepository,
                ruleFailureTelemetryRepository,
                identifierAssignmentRepository,
                syncTelemetryRepository,
                videoTelemetricRepository,
                groupSubjectRepository,
                individualRelationshipRepository,
                checklistItemRepository,
                checklistRepository,
                programEncounterRepository,
                programEnrolmentRepository,
                encounterRepository,
                subjectMigrationRepository,
                userSubjectAssignmentRepository,
                subjectProgramEligibilityRepository,
                taskUnAssignmentRepository,
                taskRepository,
                userSubjectRepository,
                individualRepository,
                resetSyncRepository,
        };
        return transactionalRepositories;
    }

    private CrudRepository[] getMetadataCrudRepositories() {
        CrudRepository[] metadataCrudRepositories = {
                messageRuleRepository,
                customQueryRepository,
        };
        return metadataCrudRepositories;
    }

    private JpaRepository[] getMetadataJpaRepositories() {
        JpaRepository[] metadataRepositories = {
                groupPrivilegeRepository,
                groupRoleRepository,
                checklistItemDetailRepository,
                checklistDetailRepository,
                individualRelationGenderMappingRepository,
                individualRelationshipTypeRepository,
                individualRelationRepository,
                formElementRepository,
                formElementGroupRepository,
                formMappingRepository,
                formRepository,
                answerConceptMigrationRepository,
                conceptAnswerRepository,
                conceptRepository,
                operationalEncounterTypeRepository,
                encounterTypeRepository,
                operationalProgramRepository,
                programRepository,
                operationalSubjectTypeRepository,
                subjectTypeRepository,
                translationRepository,
                videoRepository,
                dashboardSectionCardMappingRepository,
                cardRepository,
                dashboardSectionRepository,
                groupDashboardRepository,
                dashboardFilterRepository,
                dashboardRepository,
                documentationItemRepository,
                documentationRepository,
                menuItemRepository,
                ruleRepository,
                ruleDependencyRepository,
                taskTypeRepository,
                taskStatusRepository,
                translationRepository,
                userSubjectAssignmentRepository,
        };
        return metadataRepositories;
    }

    private CrudRepository[] getAdminConfigCrudRepositories() {
        CrudRepository[] adminConfigCrudRepositories = {
                externalSystemConfigRepository,
        };
        return adminConfigCrudRepositories;
    }

    private JpaRepository[] getAdminConfigJPARepositories() {
        JpaRepository[] adminConfigRepositories = {
                msg91ConfigRepository,
                identifierUserAssignmentRepository,
                identifierSourceRepository,
                genderRepository,
                catchmentRepository,
                locationMappingRepository,
                locationRepository,
                addressLevelTypeRepository,
                storageManagementConfigRepository,
                organisationConfigRepository,
        };
        return adminConfigRepositories;
    }

    public void addOrganisationConfig(Long orgId, ZipOutputStream zos) throws IOException {
        OrganisationConfig organisationConfig = organisationConfigRepository.findByOrganisationId(orgId);
        if (organisationConfig != null) {
            addFileToZip(zos, "organisationConfig.json", OrganisationConfigRequest.fromOrganisationConfig(organisationConfig));
            addDirectoryToZip(zos, "extensions");
            List<S3ExtensionFile> s3ExtensionFiles = s3Service.listExtensionFiles(Optional.empty());
            for (S3ExtensionFile s3ExtensionFile : s3ExtensionFiles) {
                S3File s3File = s3ExtensionFile.getS3File();
                File file = s3Service.downloadFile(s3File);
                addFileToZip(zos, s3File.getExtensionFilePathRelativeToOrganisation(), file);
            }
        }
    }

    public void addVideoJson(ZipOutputStream zos) throws IOException {
        List<VideoContract> videoContracts = videoService.getAllVideos();
        if (!videoContracts.isEmpty()) {
            addFileToZip(zos, "video.json", videoContracts);
        }
    }

    public void addRelationJson(ZipOutputStream zos) throws IOException {
        List<IndividualRelationContract> individualRelationContractList = individualRelationService.getAll();
        if (!individualRelationContractList.isEmpty()) {
            addFileToZip(zos, "individualRelation.json", individualRelationContractList);
        }
    }

    public void addRelationShipTypeJson(ZipOutputStream zos) throws IOException {
        List<IndividualRelationshipTypeContract> allRelationshipTypes = individualRelationshipTypeService.getAllRelationshipTypes(true);
        if (!allRelationshipTypes.isEmpty()) {
            addFileToZip(zos, "relationshipType.json", allRelationshipTypes);
        }
    }

    public void addIdentifierSourceJson(ZipOutputStream zos, boolean includeLocations) throws IOException {
        List<IdentifierSource> identifierSources = identifierSourceRepository.findAll();
        List<IdentifierSourceContract> identifierSourceContractWeb = identifierSources.stream().map(IdentifierSourceContract::fromIdentifierSource)
                .filter(idSource -> includeLocations || idSource.getCatchmentUUID() == null)
                .collect(Collectors.toList());
        if (!identifierSourceContractWeb.isEmpty()) {
            addFileToZip(zos, "identifierSource.json", identifierSourceContractWeb);
        }
    }

    public void addChecklistDetailJson(ZipOutputStream zos) throws IOException {
        List<ChecklistDetailRequest> allChecklistDetail = checklistDetailService.getAllChecklistDetail();
        if (!allChecklistDetail.isEmpty()) {
            addFileToZip(zos, "checklist.json", allChecklistDetail);
        }
    }

    public void addGroupsJson(ZipOutputStream zos) throws IOException {
        List<GroupContract> groups = groupRepository.findAll().stream()
                .filter(group -> !group.isAdministrator())
                .map(GroupContract::fromEntity).collect(Collectors.toList());
        if (!groups.isEmpty()) {
            addFileToZip(zos, "groups.json", groups);
        }
    }

    public void addGroupPrivilegeJson(ZipOutputStream zos) throws IOException {
        List<GroupPrivilegeBundleContract> groupPrivileges = groupPrivilegeRepository.findAll().stream()
                .filter(groupPrivilege -> !groupPrivilege.getGroup().isAdministrator())
                .filter(groupPrivilege -> groupPrivilege.getImplVersion() == GroupPrivilege.IMPL_VERSION)
                .map(GroupPrivilegeBundleContract::fromEntity).collect(Collectors.toList());
        if (!groupPrivileges.isEmpty()) {
            addFileToZip(zos, "groupPrivilege.json", groupPrivileges);
        }
    }

    public void addGroupRoleJson(ZipOutputStream zos) throws IOException {
        List<GroupRoleContract> groupRoles = groupRoleRepository.findAll().stream()
                .map(GroupRoleContract::fromEntity).collect(Collectors.toList());
        if (!groupRoles.isEmpty()) {
            addFileToZip(zos, "groupRole.json", groupRoles);
        }
    }

    public void addFormMappingsJson(Long orgId, ZipOutputStream zos) throws IOException {
        List<FormMapping> formMappings = formMappingRepository.findAllByOrganisationId(orgId);
        List<FormMappingContract> contracts = formMappings.stream()
                .map(FormMappingContract::fromFormMapping)
                .collect(Collectors.toList());
        addFileToZip(zos, "formMappings.json", contracts);
    }

    public void addOperationalProgramsJson(Organisation organisation, ZipOutputStream zos) throws IOException {
        List<OperationalProgram> operationalPrograms = operationalProgramRepository
                .findAllByOrganisationId(organisation.getId());
        List<OperationalProgramContract> contracts = operationalPrograms.stream()
                .map(OperationalProgramContract::fromOperationalProgram)
                .collect(Collectors.toList());
        OperationalProgramsContract operationalProgramsContract = new OperationalProgramsContract();
        operationalProgramsContract.setOperationalPrograms(contracts);
        addFileToZip(zos, "operationalPrograms.json", operationalProgramsContract);
    }

    public void addProgramsJson(Organisation organisation, ZipOutputStream zos) throws IOException {
        List<Program> programs = programRepository.findAllByOrganisationId(organisation.getId());
        List<ProgramRequest> contracts = programs.stream().map(ProgramRequest::fromProgram)
                .collect(Collectors.toList());
        addFileToZip(zos, "programs.json", contracts);
    }

    public void addOperationalEncounterTypesJson(Organisation organisation, ZipOutputStream zos) throws IOException {
        List<OperationalEncounterType> operationalEncounterTypes = operationalEncounterTypeRepository
                .findAllByOrganisationId(organisation.getId());
        List<OperationalEncounterTypeContract> contracts = operationalEncounterTypes.stream()
                .map(OperationalEncounterTypeContract::fromOperationalEncounterType)
                .collect(Collectors.toList());
        OperationalEncounterTypesContract operationalEncounterTypesContract = new OperationalEncounterTypesContract();
        operationalEncounterTypesContract.setOperationalEncounterTypes(contracts);
        addFileToZip(zos, "operationalEncounterTypes.json", operationalEncounterTypesContract);
    }

    public void addEncounterTypesJson(Organisation organisation, ZipOutputStream zos) throws IOException {
        List<EncounterType> encounterTypes = encounterTypeRepository.findAllByOrganisationId(organisation.getId());
        List<EntityTypeContract> contracts = encounterTypes.stream().map(EntityTypeContract::fromEncounterType)
                .collect(Collectors.toList());
        addFileToZip(zos, "encounterTypes.json", contracts);
    }

    public void addOperationalSubjectTypesJson(Organisation org, ZipOutputStream zos) throws IOException {
        List<OperationalSubjectType> operationalSubjectTypes = operationalSubjectTypeRepository
                .findAllByOrganisationId(org.getId());
        List<OperationalSubjectTypeContract> operationalSubjectTypeContracts = operationalSubjectTypes.stream()
                .map(OperationalSubjectTypeContract::fromOperationalSubjectType)
                .collect(Collectors.toList());
        OperationalSubjectTypesContract operationalSubjectTypesContract = new OperationalSubjectTypesContract();
        operationalSubjectTypesContract.setOperationalSubjectTypes(operationalSubjectTypeContracts);
        addFileToZip(zos, "operationalSubjectTypes.json", operationalSubjectTypesContract);

    }

    public void addSubjectTypesJson(Long orgId, ZipOutputStream zos) throws IOException {
        Stream<SubjectType> subjectTypes = subjectTypeRepository.findAllByOrganisationId(orgId).stream();
        List<SubjectTypeContract> subjectTypeContracts = subjectTypes.map(SubjectTypeContract::fromSubjectType)
                .collect(Collectors.toList());
        addFileToZip(zos, "subjectTypes.json", subjectTypeContracts);
    }

    public void addCatchmentsJson(Organisation organisation, ZipOutputStream zos) throws IOException {
        Stream<Catchment> allCatchments = catchmentRepository.findAllByOrganisationId(organisation.getId()).stream();
        List<CatchmentExport> catchmentExports = allCatchments.map(CatchmentExport::fromCatchment).collect(Collectors.toList());
        CatchmentsExport catchmentsExport = new CatchmentsExport();
        catchmentsExport.setCatchments(catchmentExports);
        addFileToZip(zos, "catchments.json", catchmentsExport);
    }

    public void addAddressLevelsJson(Long orgId, ZipOutputStream zos) throws IOException {
        List<LocationContract> contracts = new ArrayList<>();
        List<AddressLevel> allAddressLevels = locationRepository.findAllByOrganisationId(orgId);
        List<AddressLevel> rootNodes = allAddressLevels.stream()
                .filter(addressLevel -> addressLevel.getParent() == null)
                .collect(Collectors.toList());
        for (AddressLevel node : rootNodes) {
            addAddressLevel(node, allAddressLevels, contracts);
        }
        addFileToZip(zos, "locations.json", contracts);
    }

    private void addAddressLevel(AddressLevel theNode, List<AddressLevel> allAddressLevels, List<LocationContract> contracts) {
        List<AddressLevel> childNodes = allAddressLevels.stream()
                .filter(addressLevelType -> {
                    AddressLevel parent = addressLevelType.getParent();
                    return parent != null && parent.getId().equals(theNode.getId());
                })
                .collect(Collectors.toList());
        contracts.add(LocationContract.fromAddressLevel(theNode));
        for (AddressLevel child : childNodes) {
            addAddressLevel(child, allAddressLevels, contracts);
        }
    }

    public void addAddressLevelTypesJson(Long orgId, ZipOutputStream zos) throws IOException {

        List<AddressLevelTypeContract> contracts = new ArrayList<>();
        List<AddressLevelType> allAddressLevelTypes = addressLevelTypeRepository.findAllByOrganisationId(orgId);
        List<AddressLevelType> rootNodes = allAddressLevelTypes.stream()
                .filter(addressLevelType -> addressLevelType.getParent() == null)
                .collect(Collectors.toList());
        for (AddressLevelType node : rootNodes) {
            addAddressLevelType(node, allAddressLevelTypes, contracts);
        }
        addFileToZip(zos, "addressLevelTypes.json", contracts);
    }

    private void addAddressLevelType(AddressLevelType theNode, List<AddressLevelType> allAddressLevelTypes, List<AddressLevelTypeContract> contracts) {
        List<AddressLevelType> childNodes = allAddressLevelTypes.stream()
                .filter(addressLevelType -> {
                    AddressLevelType parent = addressLevelType.getParent();
                    return parent != null && parent.getId().equals(theNode.getId());
                })
                .collect(Collectors.toList());
        contracts.add(AddressLevelTypeContract.fromAddressLevelType(theNode));
        for (AddressLevelType child : childNodes) {
            addAddressLevelType(child, allAddressLevelTypes, contracts);
        }
    }

    public void addConceptsJson(Long orgId, ZipOutputStream zos) throws IOException {
        List<Concept> allConcepts = conceptRepository.findAllByOrganisationId(orgId);
        List<ConceptExport> conceptContracts = allConcepts.stream()
                .sorted(Comparator.comparing(Concept::getName, String::compareToIgnoreCase))
                .map(ConceptExport::fromConcept)
                .collect(Collectors.toList());
        addFileToZip(zos, "concepts.json", conceptContracts);
    }

    public void addFormsJson(Long orgId, ZipOutputStream zos) throws IOException {
        List<Form> forms = formRepository.findAllByOrganisationId(orgId);
        if (forms.size() > 1) {
            addDirectoryToZip(zos, "forms");
        }
        for (Form form : forms) {
            FormContract formContract = FormContract.fromForm(form);
            addFileToZip(zos, String.format("forms/%s.json", form.getName().replaceAll("/", "_")), formContract);
        }
    }

    public void addSubjectTypeIcons(ZipOutputStream zos) throws IOException {
        List<SubjectType> subjectTypes = subjectTypeRepository.findAllByIconFileS3KeyNotNull();
        if (!subjectTypes.isEmpty()) {
            addDirectoryToZip(zos, BundleFolder.SUBJECT_TYPE_ICONS.getFolderName());
        }
        for (SubjectType subjectType : subjectTypes) {
            InputStream objectContent = s3Service.getObjectContentFromUrl(subjectType.getIconFileS3Key());
            String extension = S.getLastStringAfter(subjectType.getIconFileS3Key(), ".");
            addMediaToZip(zos, String.format("%s/%s.%s", BundleFolder.SUBJECT_TYPE_ICONS.getFolderName(), subjectType.getUuid(), extension), IOUtils.toByteArray(objectContent));
        }
    }

    public void addReportCardIcons(ZipOutputStream zos) throws IOException {
        List<ReportCard> cards = cardRepository.findAllByIconFileS3KeyNotNull().stream()
                .filter(card -> !card.getIconFileS3Key().trim().isEmpty()).collect(Collectors.toList());
        if (!cards.isEmpty()) {
            addDirectoryToZip(zos, BundleFolder.REPORT_CARD_ICONS.getFolderName());
        }
        for (ReportCard reportCard : cards) {
            if (StringUtils.isEmpty(reportCard.getIconFileS3Key())) continue;
            InputStream objectContent = s3Service.getObjectContentFromUrl(reportCard.getIconFileS3Key());
            String extension = S.getLastStringAfter(reportCard.getIconFileS3Key(), ".");
            addMediaToZip(zos, String.format("%s/%s.%s", BundleFolder.REPORT_CARD_ICONS.getFolderName(), reportCard.getUuid(), extension), IOUtils.toByteArray(objectContent));
        }
    }

    public void addConceptMedia(ZipOutputStream zos) throws IOException {
        List<Concept> conceptsWithMedia = conceptRepository.findAllByMediaUrlNotNull().stream()
                .filter(concept -> StringUtils.hasText(concept.getMediaUrl())).toList();
        if (!conceptsWithMedia.isEmpty()) {
            addDirectoryToZip(zos, BundleFolder.CONCEPT_MEDIA.getFolderName());
        }
        for (Concept concept : conceptsWithMedia) {
            InputStream objectContent = s3Service.getObjectContentFromUrl(concept.getMediaUrl());
            String extension = S.getLastStringAfter(concept.getMediaUrl(), ".");
            addMediaToZip(zos, String.format("%s/%s.%s", BundleFolder.CONCEPT_MEDIA.getFolderName(), concept.getUuid(), extension), IOUtils.toByteArray(objectContent));
        }
    }

    public void addReportCards(ZipOutputStream zos) throws IOException {
        List<ReportCardBundleContract> cards = cardService.getAll().stream().map(reportCardMapper::toBundle).collect(Collectors.toList());
        if (!cards.isEmpty()) {
            addFileToZip(zos, "reportCard.json", cards);
        }
    }

    public void addReportDashboard(ZipOutputStream zos) throws IOException {
        List<Dashboard> dashboards = dashboardRepository.findAll();
        List<DashboardBundleContract> dashboardBundleContracts = dashboards.stream().map(dashboardMapper::toBundle).collect(Collectors.toList());
        if (!dashboardBundleContracts.isEmpty()) {
            addFileToZip(zos, "reportDashboard.json", dashboardBundleContracts);
        }
    }

    public void addDocumentation(ZipOutputStream zos) throws IOException {
        List<DocumentationContract> documentationContracts = documentationService.getAll();
        if (!documentationContracts.isEmpty()) {
            addFileToZip(zos, "documentations.json", documentationContracts);
        }
    }

    public void addTaskType(ZipOutputStream zos) throws IOException {
        List<TaskTypeContract> taskTypeContracts = taskTypeService.getAll();
        if (!taskTypeContracts.isEmpty()) {
            addFileToZip(zos, "taskType.json", taskTypeContracts);
        }
    }

    public void addTaskStatus(ZipOutputStream zos) throws IOException {
        List<TaskStatusContract> taskStatusContracts = taskStatusService.getAll();
        if (!taskStatusContracts.isEmpty()) {
            addFileToZip(zos, "taskStatus.json", taskStatusContracts);
        }
    }

    public void addApplicationMenus(ZipOutputStream zos) throws IOException {
        addFileToZip(zos, "menuItem.json", menuItemService.findAll().stream().map(MenuItemContract::new).collect(Collectors.toList()));
    }

    public void addMessageRules(ZipOutputStream zos) throws IOException {
        addFileToZip(zos, "messageRule.json", messagingService.findAll().stream().map(messageRule -> new MessageRuleContract(messageRule, entityTypeRetrieverService)).collect(Collectors.toList()));
    }

    public void addTranslations(Long orgId, ZipOutputStream zos) throws IOException {
        List<Translation> translations = translationRepository.findAllByOrganisationId(orgId);
        if (translations.isEmpty()) {
            return;
        }
        addDirectoryToZip(zos, "translations");
        for (Translation translation : translations) {
            addFileToZip(zos, String.format("translations/%s.json", translation.getLanguage()), translation.getTranslationJson());
        }
    }

    public void addOldRuleDependency(Long orgId, ZipOutputStream zos) throws IOException {
        RuleDependency ruleDependency = ruleDependencyRepository.findByOrganisationId(orgId);
        if (ruleDependency == null) {
            return;
        }
        addFileToZip(zos, "ruleDependency.json", RuleDependencyRequest.fromRuleDependency(ruleDependency));
    }

    public void addOldRules(Long orgId, ZipOutputStream zos) throws IOException {
        List<Rule> rulesFromDB = ruleRepository.findByOrganisationId(orgId);
        if (rulesFromDB.isEmpty()) {
            return;
        }
        addDirectoryToZip(zos, "oldRules");
        for (Rule rule : rulesFromDB) {
            addFileToZip(zos, String.format("oldRules/%s.json", rule.getUuid()), RuleRequest.fromRule(rule));
        }
    }

    private void addFileToZip(ZipOutputStream zos, String fileName, File file) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        byte[] bytes = Files.readAllBytes(file.toPath());
        zos.write(bytes);
        zos.closeEntry();
    }

    private void addFileToZip(ZipOutputStream zos, String fileName, Object fileContent) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        if (fileContent != null) {
            PrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
            byte[] bytes = ObjectMapperSingleton.getObjectMapper().writer(prettyPrinter).writeValueAsBytes(fileContent);
            zos.write(bytes);
        }
        zos.closeEntry();
    }

    private void addMediaToZip(ZipOutputStream zos, String fileName, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        if (bytes != null) {
            zos.write(bytes);
        }
        zos.closeEntry();
    }

    private void addDirectoryToZip(ZipOutputStream zos, String directoryName) throws IOException {
        ZipEntry entry = new ZipEntry(String.format("%s/", directoryName));
        zos.putNextEntry(entry);
        zos.closeEntry();
    }

    public void deleteTransactionalData(Organisation organisation) {
        deleteNonRepositoryTransactionalData(organisation);
        Arrays.asList(getTxCrudRepositories()).forEach(this::deleteAll);
        Arrays.asList(getTxJpaRepositories()).forEach(this::deleteAll);
    }

    public void deleteMetadata(Organisation organisation) {
        deleteNonRepositoryMetadata(organisation);
        Arrays.asList(getMetadataJpaRepositories()).forEach(this::deleteAll);
        Arrays.asList(getMetadataCrudRepositories()).forEach(this::deleteAll);
        userRepository.findAllByOrganisationId(organisation.getId()).stream().forEach(user -> user.setSyncSettings(new JsonObject()));
    }

    public void deleteNonRepositoryTransactionalData(Organisation organisation) {
        String individualRelativeDeletionQuery = "delete from individual_relative where organisation_id = %d and organisation_id > 1";
        jdbcTemplate.execute(String.format(individualRelativeDeletionQuery, organisation.getId()));
    }

    public void deleteNonRepositoryMetadata(Organisation organisation) {
        String decisionConceptsDeletionQuery = "delete from decision_concept dc using concept c where dc.concept_id = c.id and c.organisation_id = %d and c.organisation_id > 1";
        String decisionConceptsFromFormsDeletionQuery = "delete from decision_concept dc using form f where dc.form_id = f.id and f.organisation_id = %d and f.organisation_id > 1";
        String nonApplicableFormElementDeletionQuery = "delete from non_applicable_form_element where organisation_id = %d and organisation_id > 1";
        jdbcTemplate.execute(String.format(decisionConceptsDeletionQuery, organisation.getId()));
        jdbcTemplate.execute(String.format(decisionConceptsFromFormsDeletionQuery, organisation.getId()));
        jdbcTemplate.execute(String.format(nonApplicableFormElementDeletionQuery, organisation.getId()));
    }

    public void deleteAdminConfigData(Organisation organisation) {
        removeCatchmentAssignmentAndDeleteNonAdminUsers(organisation);
        deleteNonDefaultGroupsAndTheirMappings();
        deleteNonRepositoryAdminConfigData(organisation);
        Arrays.asList(getAdminConfigCrudRepositories()).forEach(this::deleteAll);
        Arrays.asList(getAdminConfigJPARepositories()).forEach(this::deleteAll);
    }

    private void deleteNonDefaultGroupsAndTheirMappings() {
        List<Group> nonDefaultGroups = groupRepository.findAll().stream()
                .filter(group -> !group.isOneOfTheDefaultGroups())
                .collect(Collectors.toList());
        if(nonDefaultGroups != null && !nonDefaultGroups.isEmpty()) {
            userGroupRepository.deleteAllByGroupIn(nonDefaultGroups);
            groupRepository.deleteAll(nonDefaultGroups);
        }
    }

    private void removeCatchmentAssignmentAndDeleteNonAdminUsers(Organisation organisation) {
        userRepository.findAllByOrganisationId(organisation.getId()).stream().forEach(user -> user.removeCatchment());
        userRepository.findAllByOrganisationId(organisation.getId()).stream()
                .filter(user -> !user.hasAllPrivileges()).forEach(user -> userService.deleteUser(user.getId()));
    }

    public void deleteNonRepositoryAdminConfigData(Organisation organisation) {
        String catchmentAddressMappingDeletionQuery = "delete from catchment_address_mapping cam using address_level al where cam.addresslevel_id = al.id and al.organisation_id = %d and al.organisation_id > 1";
        jdbcTemplate.execute(String.format(catchmentAddressMappingDeletionQuery, organisation.getId()));
    }

    private void deleteAll(JpaRepository repository) {
        repository.deleteAllInBatch();
    }

    private void deleteAll(CrudRepository repository) {
        repository.deleteAll();
    }

    public void deleteMediaContent(boolean deleteMetadata) {
        try {
            s3Service.deleteOrgMedia(deleteMetadata);
        } catch (Exception e) {
            logger.info("Error while deleting the media files, skipping.");
        }
    }

    public void deleteETLData(Organisation organisation) {
        String baseQuery = "select delete_etl_metadata_for_schema('$impl_schema', '$impl_db_user', '$impl_db_owner')";
        String query = baseQuery
                .replace("$impl_schema", organisation.getSchemaName())
                .replace("$impl_db_user", organisation.getDbUser())
                .replace("$impl_db_owner", organisation.getDbUser());
        jdbcTemplate.execute(query);
    }

    public void addGroupDashboardJson(ZipOutputStream zos) throws IOException {
        List<GroupDashboardBundleContract> groupDashboards = groupDashboardRepository.findAll().stream()
                .map(GroupDashboardBundleContract::fromEntity).collect(Collectors.toList());
        if (!groupDashboards.isEmpty()) {
            addFileToZip(zos, "groupDashboards.json", groupDashboards);
        }
    }

    private void createGender(String genderName, Organisation org) {
        if (Objects.nonNull(genderRepository.findByNameAndOrganisationId(genderName, org.getId()))) {
            return;
        }
        Gender gender = new Gender();
        gender.setName(genderName);
        gender.assignUUID();
        gender.setOrganisationId(org.getId());
        genderRepository.save(gender);
    }

    private Group addDefaultGroupIfNotPresent(Long organisationId, String groupType) {
        Group existingGroup = groupRepository.findByNameAndOrganisationId(groupType, organisationId);
        if (Objects.nonNull(existingGroup)) {
            return existingGroup;
        }
        Group group = new Group();
        group.setName(groupType);
        group.setOrganisationId(organisationId);
        group.setUuid(UUID.randomUUID().toString());
        group.setHasAllPrivileges(group.isAdministrator());
        group.setVersion(0);
        return groupRepository.save(group);
    }

    private void createDefaultGenders(Organisation org) {
        createGender("Male", org);
        createGender("Female", org);
        createGender("Other", org);
    }

    public void setupBaseOrganisationData(Organisation organisation) {
        setupBaseOrganisationMetadata(organisation);
        setupBaseOrganisationAdminConfig(organisation);
    }

    public void setupBaseOrganisationAdminConfig(Organisation organisation) {
        organisationConfigService.createDefaultOrganisationConfig(organisation);
    }

    public void setupBaseOrganisationMetadata(Organisation organisation) {
        createDefaultGenders(organisation);
        Group everyoneGroup = addDefaultGroupIfNotPresent(organisation.getId(), Group.Everyone);
        addDefaultGroupIfNotPresent(organisation.getId(), Group.Administrators);
        Dashboard defaultDashboard = dashboardService.createDefaultDashboard(organisation);
        groupDashboardService.createDefaultGroupDashboardForOrg(organisation, everyoneGroup, defaultDashboard);
    }

    public Organisation getCurrentOrganisation() {
        Long organisationId = UserContextHolder.getUserContext().getOrganisationId();
        return organisationRepository.findOne(organisationId);
    }

    public void addCustomQueries(Long orgId, ZipOutputStream zos) throws IOException {
        List<CustomQuery> customQueryList = customQueryRepository.findByOrganisationId(orgId);
        List<CustomQueryContract> customQueryContractList = customQueryList.stream().map(customQueryService::EntityToDto).collect(Collectors.toList());
        if (customQueryList.isEmpty()) {
            return;
        }
        addFileToZip(zos, "customQueries.json", customQueryContractList);
    }
}
