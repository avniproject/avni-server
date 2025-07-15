package org.avni.server.importer.batch.zip;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.messaging.contract.MessageRuleContract;
import org.avni.messaging.service.MessagingService;
import org.avni.server.builder.FormBuilderException;
import org.avni.server.dao.CardRepository;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.Locale;
import org.avni.server.domain.*;
import org.avni.server.framework.security.AuthService;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.importer.batch.AvniSpringBatchJobHelper;
import org.avni.server.importer.batch.model.BundleFile;
import org.avni.server.importer.batch.model.BundleFolder;
import org.avni.server.importer.batch.model.BundleZip;
import org.avni.server.service.*;
import org.avni.server.service.accessControl.GroupPrivilegeService;
import org.avni.server.service.application.MenuItemService;
import org.avni.server.service.media.MediaFolder;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.contract.GroupDashboardBundleContract;
import org.avni.server.web.contract.reports.DashboardBundleContract;
import org.avni.server.web.request.*;
import org.avni.server.web.request.application.ChecklistDetailRequest;
import org.avni.server.web.request.application.FormContract;
import org.avni.server.web.request.application.menu.MenuItemContract;
import org.avni.server.web.request.reports.ReportCardBundleRequest;
import org.avni.server.web.request.webapp.documentation.DocumentationContract;
import org.avni.server.web.request.webapp.task.TaskStatusContract;
import org.avni.server.web.request.webapp.task.TaskTypeContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
@JobScope
public class BundleZipFileImporter implements ItemWriter<BundleFile> {
    private static final Logger logger = LoggerFactory.getLogger(BundleZipFileImporter.class);

    private static final char SEPARATOR_FOR_EXTENSION = '.';
    private final AuthService authService;
    private final EncounterTypeService encounterTypeService;
    private final FormMappingService formMappingService;
    private final OrganisationConfigService organisationConfigService;

    private final ObjectMapper objectMapper;
    private final ConceptService conceptService;
    private final FormService formService;
    private final LocationService locationService;
    private final CatchmentService catchmentService;
    private final SubjectTypeService subjectTypeService;
    private final ProgramService programService;
    private final IndividualRelationService individualRelationService;
    private final IndividualRelationshipTypeService individualRelationshipTypeService;
    private final ChecklistDetailService checklistDetailService;
    private final IdentifierSourceService identifierSourceService;
    private final GroupsService groupsService;
    private final GroupRoleService groupRoleService;
    private final SubjectTypeRepository subjectTypeRepository;
    private final CardRepository cardRepository;
    private final GroupPrivilegeService groupPrivilegeService;
    private final VideoService videoService;
    private final CardService cardService;
    private final DashboardService dashboardService;
    private final S3Service s3Service;
    private final DocumentationService documentationService;
    private final TaskTypeService taskTypeService;
    private final TaskStatusService taskStatusService;
    private final MenuItemService menuItemService;
    private final MessagingService messagingService;
    private final RuleDependencyService ruleDependencyService;
    private final TranslationService translationService;
    private final RuleService ruleService;
    private final GroupDashboardService groupDashboardService;
    private final CustomQueryService customQueryService;
    private final ConceptRepository conceptRepository;
    private final AvniSpringBatchJobHelper avniSpringBatchJobHelper;

    @Value("#{jobParameters['userId']}")
    private Long userId;
    @Value("#{jobParameters['organisationUUID']}")
    private String organisationUUID;

    /**
     * IMPORTANT: The un-tampered bundle is processed in the order of files inserted while generating the bundle,
     * which is as per code in ImplementationController.export().
     * <p>
     * Always ensure that bundle is created with content in the same sequence that you want it to be processed during upload.
     * DISCLAIMER: If the bundle is tampered, for example to remove any forms or concepts, then the sequence of processing of bundle files is unknown
     */
    private final List<String> fileSequence = new ArrayList<String>() {{
        add("organisationConfig.json");
        add("addressLevelTypes.json");
        add("locations.json");
        add("catchments.json");
        add("subjectTypes.json");
        add("operationalSubjectTypes.json");
        add("programs.json");
        add("operationalPrograms.json");
        add("encounterTypes.json");
        add("operationalEncounterTypes.json");
        add("documentations.json");
        add("concepts.json");
        add(BundleFolder.FORMS.getFolderName());
        add("formMappings.json");
        add("individualRelation.json");
        add("relationshipType.json");
        add("identifierSource.json");
        add("checklist.json");
        add("groups.json");
        add("groupRole.json");
        add("groupPrivilege.json");
        add("video.json");
        add("reportCard.json");
        add("reportDashboard.json");
        add("groupDashboards.json");
        add("taskType.json");
        add("taskStatus.json");
        add("menuItem.json");
        add("messageRule.json");
        add(BundleFolder.TRANSLATIONS.getFolderName());
        add("ruleDependency.json");
        add(BundleFolder.OLD_RULES.getFolderName());
        add(BundleFolder.SUBJECT_TYPE_ICONS.getFolderName());
        add(BundleFolder.REPORT_CARD_ICONS.getFolderName());
        add(BundleFolder.CONCEPT_MEDIA.getFolderName());
        add("customQueries.json");
    }};


    @Autowired
    public BundleZipFileImporter(AuthService authService,
                                 ConceptService conceptService,
                                 FormService formService,
                                 LocationService locationService,
                                 CatchmentService catchmentService,
                                 SubjectTypeService subjectTypeService,
                                 ProgramService programService,
                                 EncounterTypeService encounterTypeService,
                                 FormMappingService formMappingService,
                                 OrganisationConfigService organisationConfigService,
                                 IndividualRelationService individualRelationService,
                                 IndividualRelationshipTypeService individualRelationshipTypeService,
                                 ChecklistDetailService checklistDetailService,
                                 IdentifierSourceService identifierSourceService,
                                 GroupsService groupsService,
                                 GroupRoleService groupRoleService,
                                 SubjectTypeRepository subjectTypeRepository,
                                 CardRepository cardRepository,
                                 GroupPrivilegeService groupPrivilegeService,
                                 VideoService videoService,
                                 CardService cardService,
                                 DashboardService dashboardService, @Qualifier("BatchS3Service") S3Service s3Service,
                                 DocumentationService documentationService,
                                 TaskTypeService taskTypeService,
                                 TaskStatusService taskStatusService,
                                 MenuItemService menuItemService,
                                 MessagingService messagingService,
                                 RuleDependencyService ruleDependencyService,
                                 TranslationService translationService,
                                 RuleService ruleService, GroupDashboardService groupDashboardService, CustomQueryService customQueryService, ConceptRepository conceptRepository,
                                 AvniSpringBatchJobHelper avniSpringBatchJobHelper) {
        this.authService = authService;
        this.conceptService = conceptService;
        this.formService = formService;
        this.locationService = locationService;
        this.catchmentService = catchmentService;
        this.subjectTypeService = subjectTypeService;
        this.programService = programService;
        this.encounterTypeService = encounterTypeService;
        this.formMappingService = formMappingService;
        this.organisationConfigService = organisationConfigService;
        this.individualRelationService = individualRelationService;
        this.individualRelationshipTypeService = individualRelationshipTypeService;
        this.checklistDetailService = checklistDetailService;
        this.identifierSourceService = identifierSourceService;
        this.groupsService = groupsService;
        this.groupRoleService = groupRoleService;
        this.subjectTypeRepository = subjectTypeRepository;
        this.cardRepository = cardRepository;
        this.groupPrivilegeService = groupPrivilegeService;
        this.videoService = videoService;
        this.cardService = cardService;
        this.dashboardService = dashboardService;
        this.s3Service = s3Service;
        this.documentationService = documentationService;
        this.taskTypeService = taskTypeService;
        this.taskStatusService = taskStatusService;
        this.menuItemService = menuItemService;
        this.messagingService = messagingService;
        this.ruleDependencyService = ruleDependencyService;
        this.translationService = translationService;
        this.ruleService = ruleService;
        this.groupDashboardService = groupDashboardService;
        this.customQueryService = customQueryService;
        this.conceptRepository = conceptRepository;
        this.avniSpringBatchJobHelper = avniSpringBatchJobHelper;
        objectMapper = ObjectMapperSingleton.getObjectMapper();
    }

    @Override
    public void write(Chunk<? extends BundleFile> chunk) throws Exception {
        authService.authenticateByUserId(userId, organisationUUID);
        List<? extends BundleFile> bundleFiles = chunk.getItems();
        BundleZip bundleZip = new BundleZip(bundleFiles.stream().collect(Collectors.toMap(BundleFile::getName, BundleFile::getContent)));
        for (String filename : fileSequence) {
            Optional<BundleFolder> fromFileName = BundleFolder.getFromFileName(filename);
            if (fromFileName.isPresent()) {
                deployFolder(fromFileName.get(), bundleZip);
            } else {
                deployFileIfDataExists(filename, bundleZip);
            }
        }
        List<String> extensions = bundleZip.getExtensionNames();
        for (String fileName : extensions) {
            deployExtensionFiles(fileName, bundleZip.getFile(fileName));
        }
    }

    private String uploadMedia(String folderName, String mediaFileName, byte[] mediaFileData) throws IOException {
        logger.info("uploading media {}", mediaFileName);
        String[] splitFileName = mediaFileName.split("\\.");
        String entityUUID = splitFileName[0];
        String extension = splitFileName[1];
        return s3Service.uploadByteArray(entityUUID, extension, folderName, mediaFileData);
    }

    private void deployFileIfDataExists(String filename, BundleZip bundleZip) throws IOException {
        byte[] fileData = bundleZip.getFile(filename);
        if (fileData != null) {
            deployFile(filename, new String(fileData, StandardCharsets.UTF_8));
        }
    }

    private void deployFolder(BundleFolder bundleFolder, BundleZip bundleZip) throws IOException, FormBuilderException {
        Map<String, byte[]> files = bundleZip.getFileNameAndDataInFolder(bundleFolder.getFolderName());
        for (Map.Entry fileEntry : files.entrySet()) {
            deployFolder(bundleFolder, fileEntry);
        }
    }

    private void deployFile(String fileName, String fileData) throws IOException {
        logger.info("processing file {}", fileName);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        switch (fileName) {
            case "organisationConfig.json":
                OrganisationConfigRequest organisationConfigRequest = convertString(fileData, OrganisationConfigRequest.class);
                organisationConfigService.saveOrganisationConfig(organisationConfigRequest, organisation);
                break;
            case "addressLevelTypes.json":
                AddressLevelTypeContract[] addressLevelTypeContracts = convertString(fileData, AddressLevelTypeContract[].class);
                locationService.createAddressLevelTypes(organisation, addressLevelTypeContracts);
                break;
            case "locations.json":
                LocationContract[] locationContracts = convertString(fileData, LocationContract[].class);
                locationService.saveAll(Arrays.asList(locationContracts));
                break;
            case "catchments.json":
                CatchmentsContract catchmentsContract = convertString(fileData, CatchmentsContract.class);
                catchmentService.saveAllCatchments(catchmentsContract, organisation);
                break;
            case "subjectTypes.json":
                SubjectTypeContract[] subjectTypeContracts = convertString(fileData, SubjectTypeContract[].class);
                subjectTypeService.saveSubjectTypesFromBundle(subjectTypeContracts);
                break;
            case "operationalSubjectTypes.json":
                OperationalSubjectTypesContract operationalSubjectTypesContract = convertString(fileData, OperationalSubjectTypesContract.class);
                subjectTypeService.saveOperationalSubjectTypes(operationalSubjectTypesContract, organisation);
                break;
            case "programs.json":
                ProgramRequest[] programRequests = convertString(fileData, ProgramRequest[].class);
                programService.savePrograms(programRequests);
                break;
            case "operationalPrograms.json":
                OperationalProgramsContract operationalProgramsContract = convertString(fileData, OperationalProgramsContract.class);
                programService.saveOperationalPrograms(operationalProgramsContract, organisation);
                break;
            case "encounterTypes.json":
                EntityTypeContract[] entityTypeContracts = convertString(fileData, EntityTypeContract[].class);
                encounterTypeService.saveEncounterTypes(entityTypeContracts);
                break;
            case "operationalEncounterTypes.json":
                OperationalEncounterTypesContract operationalEncounterTypesContract = convertString(fileData, OperationalEncounterTypesContract.class);
                encounterTypeService.saveOperationalEncounterTypes(operationalEncounterTypesContract, organisation);
                break;
            case "documentations.json":
                DocumentationContract[] documentationContracts = convertString(fileData, DocumentationContract[].class);
                documentationService.saveDocumentations(documentationContracts);
                break;
            case "concepts.json":
                ConceptContract[] conceptContracts = convertString(fileData, ConceptContract[].class);
                conceptService.saveOrUpdateConcepts(Arrays.asList(conceptContracts), ConceptContract.RequestType.Bundle);
                break;
            case "formMappings.json":
                FormMappingContract[] formMappingContracts = convertString(fileData, FormMappingContract[].class);
                for (FormMappingContract formMappingContract : formMappingContracts) {
                    formMappingService.createOrUpdateFormMapping(formMappingContract);
                }
                break;
            case "individualRelation.json":
                IndividualRelationContract[] individualRelationContracts = convertString(fileData, IndividualRelationContract[].class);
                individualRelationService.saveRelations(individualRelationContracts);
                break;
            case "relationshipType.json":
                IndividualRelationshipTypeContract[] individualRelationshipTypeContracts = convertString(fileData, IndividualRelationshipTypeContract[].class);
                individualRelationshipTypeService.saveRelationshipTypes(individualRelationshipTypeContracts);
                break;
            case "identifierSource.json":
                IdentifierSourceContract[] identifierSourceContracts = convertString(fileData, IdentifierSourceContract[].class);
                identifierSourceService.saveIdSources(identifierSourceContracts);
                break;
            case "checklist.json":
                ChecklistDetailRequest[] checklistDetailRequests = convertString(fileData, ChecklistDetailRequest[].class);
                checklistDetailService.saveChecklists(checklistDetailRequests);
                break;
            case "groups.json":
                GroupContract[] groupContracts = convertString(fileData, GroupContract[].class);
                groupsService.saveGroups(groupContracts, organisation);
                break;
            case "groupRole.json":
                GroupRoleContract[] groupRoleContracts = convertString(fileData, GroupRoleContract[].class);
                groupRoleService.saveGroupRoles(groupRoleContracts, organisation);
                break;
            case "groupPrivilege.json":
                GroupPrivilegeBundleContract[] groupPrivilegeContracts = convertString(fileData, GroupPrivilegeBundleContract[].class);
                groupPrivilegeService.savePrivilegesFromBundle(groupPrivilegeContracts, organisation);
                break;
            case "groupDashboards.json":
                GroupDashboardBundleContract[] contracts = convertString(fileData, GroupDashboardBundleContract[].class);
                groupDashboardService.saveFromBundle(Arrays.asList(contracts));
                break;
            case "video.json":
                VideoContract[] videoContracts = convertString(fileData, VideoContract[].class);
                for (VideoContract videoContract : videoContracts) {
                    videoService.saveVideo(videoContract);
                }
                break;
            case "reportCard.json":
                ReportCardBundleRequest[] cardContracts = convertString(fileData, ReportCardBundleRequest[].class);
                cardService.saveCards(cardContracts);
                break;
            case "reportDashboard.json":
                DashboardBundleContract[] dashboardContracts = convertString(fileData, DashboardBundleContract[].class);
                dashboardService.saveDashboards(dashboardContracts);
                break;
            case "taskType.json":
                TaskTypeContract[] taskTypeContracts = convertString(fileData, TaskTypeContract[].class);
                taskTypeService.saveTaskTypes(taskTypeContracts);
                break;
            case "taskStatus.json":
                TaskStatusContract[] taskStatusContracts = convertString(fileData, TaskStatusContract[].class);
                taskStatusService.saveTaskStatuses(taskStatusContracts);
                break;
            case "menuItem.json":
                MenuItemContract[] menuItemContracts = convertString(fileData, MenuItemContract[].class);
                menuItemService.saveMenuItems(menuItemContracts);
                break;
            case "messageRule.json":
                MessageRuleContract[] messageRuleContracts = convertString(fileData, MessageRuleContract[].class);
                messagingService.saveRules(messageRuleContracts);
                break;
            case "ruleDependency.json":
                RuleDependencyRequest ruleDependencyRequest = convertString(fileData, RuleDependencyRequest.class);
                ruleDependencyService.uploadRuleDependency(ruleDependencyRequest, organisation);
                break;
            case "customQueries.json":
                List<CustomQueryContract> customQueries = convertString(fileData, new TypeReference<List<CustomQueryContract>>() {});
                customQueryService.processCustomQueries(customQueries);
                break;
        }
    }

    private void deployFolder(BundleFolder bundleFolder, Map.Entry<String, byte[]> fileData) throws IOException, FormBuilderException {
        logger.info("processing folder {} file {}", bundleFolder.getModifiedFileName(), fileData.getKey());
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        switch (bundleFolder) {
            case FORMS:
                FormContract formContract = convertString(fileData.getValue(), FormContract.class);
                formService.validateForm(formContract);
                formService.saveForm(formContract);
                break;
            case TRANSLATIONS:
                TranslationContract translationContract = new TranslationContract();
                String language = fileData.getKey().substring(0, fileData.getKey().indexOf(SEPARATOR_FOR_EXTENSION));
                translationContract.setLanguage(Locale.valueOf(language));
                translationContract.setTranslationJson(convertString(fileData.getValue(), JsonObject.class));
                translationService.uploadTranslations(translationContract, organisation);
                break;
            case OLD_RULES:
                RuleRequest ruleRequest = convertString(fileData.getValue(), RuleRequest.class);
                ruleService.createOrUpdate(ruleRequest, organisation);
                break;
            case SUBJECT_TYPE_ICONS:
                String stS3ObjectKey = uploadMedia(MediaFolder.ICONS.label, fileData.getKey(), fileData.getValue());
                String subjectTypeUUID = fileData.getKey().substring(0, fileData.getKey().indexOf(SEPARATOR_FOR_EXTENSION));
                SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
                subjectType.setIconFileS3Key(stS3ObjectKey);
                subjectTypeRepository.save(subjectType);
                break;
            case REPORT_CARD_ICONS:
                String cs3ObjectKey = uploadMedia(MediaFolder.ICONS.label, fileData.getKey(), fileData.getValue());
                String reportCardUUID = fileData.getKey().substring(0, fileData.getKey().indexOf(SEPARATOR_FOR_EXTENSION));
                ReportCard card = cardRepository.findByUuid(reportCardUUID);
                card.setIconFileS3Key(cs3ObjectKey);
                cardRepository.save(card);
                break;
            case CONCEPT_MEDIA:
                String medias3ObjectKey = uploadMedia(MediaFolder.MetaData.label, fileData.getKey(), fileData.getValue());
                String conceptUuid = fileData.getKey().substring(0, fileData.getKey().indexOf(SEPARATOR_FOR_EXTENSION));
                Concept concept = conceptRepository.findByUuid(conceptUuid);
                concept.setMediaUrl(medias3ObjectKey);
                concept.setMediaType(Concept.MediaType.Image);
                conceptRepository.save(concept);
                break;
        }
    }

    public void deployExtensionFiles(String filePath, byte[] contents) throws IOException {
        if (filePath.contains(OrganisationConfig.Extension.EXTENSION_DIR))
            s3Service.uploadInOrganisation(filePath, contents);
    }

    private <T> T convertString(byte[] data, Class<T> convertTo) throws IOException {
        return convertString(new String(data, StandardCharsets.UTF_8), convertTo);
    }

    private <T> T convertString(String data, Class<T> convertTo) throws IOException {
        return objectMapper.readValue(data, convertTo);
    }
    private <T> T convertString(String data, TypeReference<T> convertTo) throws IOException {
        return objectMapper.readValue(data, convertTo);
    }
}
