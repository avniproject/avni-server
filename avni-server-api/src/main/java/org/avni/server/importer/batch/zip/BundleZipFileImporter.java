package org.avni.server.importer.batch.zip;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.messaging.contract.MessageRuleContract;
import org.avni.messaging.domain.MessageRule;
import org.avni.messaging.service.MessagingService;
import org.avni.server.application.menu.MenuItem;
import org.avni.server.builder.BuilderException;
import org.avni.server.builder.FormBuilderException;
import org.avni.server.dao.CardRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.Locale;
import org.avni.server.framework.security.AuthService;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.importer.batch.model.BundleFile;
import org.avni.server.importer.batch.model.BundleFolder;
import org.avni.server.importer.batch.model.BundleZip;
import org.avni.server.service.*;
import org.avni.server.service.accessControl.GroupPrivilegeService;
import org.avni.server.service.application.MenuItemService;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.contract.GroupDashboardBundleContract;
import org.avni.server.web.request.*;
import org.avni.server.web.request.application.ChecklistDetailRequest;
import org.avni.server.web.request.application.FormContract;
import org.avni.server.web.request.application.menu.MenuItemContract;
import org.avni.server.web.request.webapp.IdentifierSourceContractWeb;
import org.avni.server.web.request.webapp.documentation.DocumentationContract;
import org.avni.server.web.request.webapp.task.TaskStatusContract;
import org.avni.server.web.request.webapp.task.TaskTypeContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

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
    private final EntityTypeRetrieverService entityTypeRetrieverService;
    private final MessagingService messagingService;
    private final RuleDependencyService ruleDependencyService;
    private final TranslationService translationService;
    private final RuleService ruleService;
    private final GroupDashboardService groupDashboardService;

    @Value("#{jobParameters['userId']}")
    private Long userId;
    @Value("#{jobParameters['organisationUUID']}")
    private String organisationUUID;

    /**
     * IMPORTANT: The un-tampered bundle is processed in the order of files inserted while generating the bundle,
     * which is as per code in ImplementationController.export().
     *
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
                                 EntityTypeRetrieverService entityTypeRetrieverService,
                                 MessagingService messagingService,
                                 RuleDependencyService ruleDependencyService,
                                 TranslationService translationService,
                                 RuleService ruleService, GroupDashboardService groupDashboardService) {
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
        this.entityTypeRetrieverService = entityTypeRetrieverService;
        this.messagingService = messagingService;
        this.ruleDependencyService = ruleDependencyService;
        this.translationService = translationService;
        this.ruleService = ruleService;
        this.groupDashboardService = groupDashboardService;
        objectMapper = ObjectMapperSingleton.getObjectMapper();
    }

    @PostConstruct
    public void authenticateUser() {
        authService.authenticateByUserId(userId, organisationUUID);
    }

    @Override
    public void write(List<? extends BundleFile> bundleFiles) throws Exception {
        BundleZip bundleZip = new BundleZip(bundleFiles.stream().collect(Collectors.toMap(BundleFile::getName, BundleFile::getContent)));
        for (String filename : fileSequence) {
            Optional<BundleFolder> fromFileName = BundleFolder.getFromFileName(filename);
            if(fromFileName.isPresent()) {
                deployFolder(fromFileName.get(), bundleFiles, bundleZip);
            } else {
                deployFileIfDataExists(bundleFiles, bundleZip, filename);
            }
        }
        List<String> extensions = bundleZip.getExtensionNames();
        for (String fileName : extensions) {
            deployFile(fileName, bundleZip.getFile(fileName));
        }
    }

    private String uploadIcon(String iconFileName, byte[] iconFileData) throws IOException {
        String bucketName = "icons";
        logger.info("uploading icon {}", iconFileName);
        String[] splitFileName = iconFileName.split("\\.");
        String entityUUID = splitFileName[0];
        String extension = splitFileName[1];
        return s3Service.uploadByteArray(entityUUID, extension, bucketName, iconFileData);
    }

    private void deployFileIfDataExists(List<? extends BundleFile> bundleFiles, BundleZip bundleZip, String filename) throws IOException, FormBuilderException, ValidationException {
        byte[] fileData = bundleZip.getFile(filename);
        if (fileData != null) {
            deployFile(filename, new String(fileData, StandardCharsets.UTF_8), bundleFiles);
        }
    }

    private void deployFolder(BundleFolder bundleFolder, List<? extends BundleFile> bundleFiles, BundleZip bundleZip) throws IOException, FormBuilderException {
        Map<String, byte[]> files = bundleZip.getFileNameAndDataInFolder(bundleFolder.getFolderName());
        for (Map.Entry fileEntry : files.entrySet()) {
            deployFile(bundleFolder, fileEntry, bundleFiles);
        }
    }

    private void deployFile(String fileName, String fileData, List<? extends BundleFile> bundleFiles) throws IOException, FormBuilderException, BuilderException, ValidationException {
        logger.info("processing file {}", fileName);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        switch (fileName) {
            case "organisationConfig.json":
                OrganisationConfigRequest organisationConfigRequest = convertString(fileData, OrganisationConfigRequest.class);
                organisationConfigService.saveOrganisationConfig(organisationConfigRequest, organisation);
                break;
            case "addressLevelTypes.json":
                AddressLevelTypeContract[] addressLevelTypeContracts = convertString(fileData, AddressLevelTypeContract[].class);
                for (AddressLevelTypeContract addressLevelTypeContract : addressLevelTypeContracts) {
                    locationService.createAddressLevelType(addressLevelTypeContract);
                }
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
                for (SubjectTypeContract subjectTypeContract : subjectTypeContracts) {
                    subjectTypeService.saveSubjectType(subjectTypeContract);
                }
                break;
            case "operationalSubjectTypes.json":
                OperationalSubjectTypesContract operationalSubjectTypesContract = convertString(fileData, OperationalSubjectTypesContract.class);
                for (OperationalSubjectTypeContract ostc : operationalSubjectTypesContract.getOperationalSubjectTypes()) {
                    subjectTypeService.createOperationalSubjectType(ostc, organisation);
                }
                break;
            case "programs.json":
                ProgramRequest[] programRequests = convertString(fileData, ProgramRequest[].class);
                for (ProgramRequest programRequest : programRequests) {
                    programService.saveProgram(programRequest);
                }
                break;
            case "operationalPrograms.json":
                OperationalProgramsContract operationalProgramsContract = convertString(fileData, OperationalProgramsContract.class);
                for (OperationalProgramContract opc : operationalProgramsContract.getOperationalPrograms()) {
                    programService.createOperationalProgram(opc, organisation);
                }
                break;
            case "encounterTypes.json":
                EntityTypeContract[] entityTypeContracts = convertString(fileData, EntityTypeContract[].class);
                for (EntityTypeContract entityTypeContract : entityTypeContracts) {
                    encounterTypeService.createEncounterType(entityTypeContract);
                }
                break;
            case "operationalEncounterTypes.json":
                OperationalEncounterTypesContract operationalEncounterTypesContract = convertString(fileData, OperationalEncounterTypesContract.class);
                for (OperationalEncounterTypeContract oetc : operationalEncounterTypesContract.getOperationalEncounterTypes()) {
                    encounterTypeService.createOperationalEncounterType(oetc, organisation);
                }
                break;
            case "documentations.json":
                DocumentationContract[] documentationContracts = convertString(fileData, DocumentationContract[].class);
                for (DocumentationContract documentationContract : documentationContracts) {
                    documentationService.saveDocumentation(documentationContract);
                }
                break;
            case "concepts.json":
                ConceptContract[] conceptContracts = convertString(fileData, ConceptContract[].class);
                conceptService.saveOrUpdateConcepts(Arrays.asList(conceptContracts));
                break;
            case "formMappings.json":
                FormMappingContract[] formMappingContracts = convertString(fileData, FormMappingContract[].class);
                for (FormMappingContract formMappingContract : formMappingContracts) {
                    formMappingService.createOrUpdateFormMapping(formMappingContract);
                }
                break;
            case "individualRelation.json":
                IndividualRelationContract[] individualRelationContracts = convertString(fileData, IndividualRelationContract[].class);
                for (IndividualRelationContract individualRelationContract : individualRelationContracts) {
                    individualRelationService.uploadRelation(individualRelationContract);
                }
                break;
            case "relationshipType.json":
                IndividualRelationshipTypeContract[] individualRelationshipTypeContracts = convertString(fileData, IndividualRelationshipTypeContract[].class);
                for (IndividualRelationshipTypeContract individualRelationshipTypeContract : individualRelationshipTypeContracts) {
                    individualRelationshipTypeService.saveRelationshipType(individualRelationshipTypeContract);
                }
                break;
            case "identifierSource.json":
                IdentifierSourceContractWeb[] identifierSourceContractWebs = convertString(fileData, IdentifierSourceContractWeb[].class);
                for (IdentifierSourceContractWeb identifierSourceContractWeb : identifierSourceContractWebs) {
                    identifierSourceService.saveIdSource(identifierSourceContractWeb);
                }
                break;
            case "checklist.json":
                ChecklistDetailRequest[] checklistDetailRequests = convertString(fileData, ChecklistDetailRequest[].class);
                for (ChecklistDetailRequest checklistDetailRequest : checklistDetailRequests) {
                    checklistDetailService.saveChecklist(checklistDetailRequest);
                }
                break;
            case "groups.json":
                GroupContract[] groupContracts = convertString(fileData, GroupContract[].class);
                for (GroupContract groupContract : groupContracts) {
                    groupsService.saveGroup(groupContract, organisation);
                }
                break;
            case "groupRole.json":
                GroupRoleContract[] groupRoleContracts = convertString(fileData, GroupRoleContract[].class);
                for (GroupRoleContract groupRoleContract : groupRoleContracts) {
                    SubjectType groupSubjectType = subjectTypeRepository.findByUuid(groupRoleContract.getGroupSubjectTypeUUID());
                    SubjectType memberSubjectType = subjectTypeRepository.findByUuid(groupRoleContract.getMemberSubjectTypeUUID());
                    groupRoleService.saveGroupRole(groupRoleContract, groupSubjectType, memberSubjectType);
                }
                break;
            case "groupPrivilege.json":
                GroupPrivilegeContractWeb[] groupPrivilegeContracts = convertString(fileData, GroupPrivilegeContractWeb[].class);
                groupPrivilegeService.savePrivileges(groupPrivilegeContracts, organisation);
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
                CardContract[] cardContracts = convertString(fileData, CardContract[].class);
                for (CardContract cardContract : cardContracts) {
                    cardService.uploadCard(cardContract);
                }
                break;
            case "reportDashboard.json":
                DashboardResponse[] dashboardContracts = convertString(fileData, DashboardResponse[].class);
                for (DashboardResponse dashboardContract : dashboardContracts) {
                    dashboardService.uploadDashboard(dashboardContract);
                }
                break;
            case "taskType.json":
                TaskTypeContract[] taskTypeContracts = convertString(fileData, TaskTypeContract[].class);
                for (TaskTypeContract taskTypeContract : taskTypeContracts) {
                    taskTypeService.saveTaskType(taskTypeContract);
                }
                break;
            case "taskStatus.json":
                TaskStatusContract[] taskStatusContracts = convertString(fileData, TaskStatusContract[].class);
                for (TaskStatusContract taskStatusContract : taskStatusContracts) {
                    taskStatusService.importTaskStatus(taskStatusContract);
                }
                break;
            case "menuItem.json":
                MenuItemContract[] menuItemContracts = convertString(fileData, MenuItemContract[].class);
                for (MenuItemContract contract : menuItemContracts) {
                    MenuItem menuItem = menuItemService.find(contract.getUuid());
                    menuItemService.save(MenuItemContract.toEntity(contract, menuItem));
                }
                break;
            case "messageRule.json":
                MessageRuleContract[] messageRuleContracts = convertString(fileData, MessageRuleContract[].class);
                for (MessageRuleContract messageRuleContract : messageRuleContracts) {
                    MessageRule messageRule = messagingService.find(messageRuleContract.getUuid());
                    messageRule = MessageRuleContract.toModel(messageRuleContract, messageRule, entityTypeRetrieverService);
                    messagingService.saveRule(messageRule);
                }
                break;
            case "ruleDependency.json":
                RuleDependencyRequest ruleDependencyRequest = convertString(fileData, RuleDependencyRequest.class);
                ruleDependencyService.uploadRuleDependency(ruleDependencyRequest, organisation);
                break;
        }
    }

    private void deployFile(BundleFolder bundleFolder, Map.Entry<String, byte[]> fileData, List<? extends BundleFile> bundleFiles) throws IOException, FormBuilderException, BuilderException {
        logger.info("processing folder {} file {}", bundleFolder.getModifiedFileName(), fileData.getKey());
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        switch (bundleFolder) {
            case FORMS:
                FormContract formContract = convertString(fileData.getValue(), FormContract.class);
                formContract.validate();
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
                String stS3ObjectKey = uploadIcon(fileData.getKey(), fileData.getValue());
                String subjectTypeUUID = fileData.getKey().substring(0, fileData.getKey().indexOf(SEPARATOR_FOR_EXTENSION));
                SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
                subjectType.setIconFileS3Key(stS3ObjectKey);
                subjectTypeRepository.save(subjectType);
                break;
            case REPORT_CARD_ICONS:
                String cs3ObjectKey = uploadIcon(fileData.getKey(), fileData.getValue());
                String reportCardUUID = fileData.getKey().substring(0, fileData.getKey().indexOf(SEPARATOR_FOR_EXTENSION));
                Card card = cardRepository.findByUuid(reportCardUUID);
                card.setIconFileS3Key(cs3ObjectKey);
                cardRepository.save(card);
                break;
        }
    }

    public void deployFile(String filePath, byte[] contents) throws IOException {
        if (filePath.contains(OrganisationConfig.Extension.EXTENSION_DIR))
            s3Service.uploadInOrganisation(filePath, contents);
    }

    private <T> T convertString(byte[] data, Class<T> convertTo) throws IOException {
        return convertString(new String(data, StandardCharsets.UTF_8),convertTo);
    }

    private <T> T convertString(String data, Class<T> convertTo) throws IOException {
        return objectMapper.readValue(data, convertTo);
    }
}
