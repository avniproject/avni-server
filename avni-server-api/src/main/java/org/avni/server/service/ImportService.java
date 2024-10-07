package org.avni.server.service;

import org.avni.server.application.FormElement;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.*;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.Locale;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.importer.batch.csv.writer.LocationWriter;
import org.avni.server.importer.batch.csv.writer.ProgramEnrolmentWriter;
import org.avni.server.importer.batch.csv.writer.header.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ImportService implements ImportLocationsConstants {

    public static final Random RANDOM = new Random();
    private final SubjectTypeRepository subjectTypeRepository;
    private final FormMappingRepository formMappingRepository;
    private static final Logger logger = LoggerFactory.getLogger(ProgramEnrolmentWriter.class);
    private final ProgramRepository programRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final OrganisationConfigRepository organisationConfigRepository;
    private final GroupRepository groupRepository;
    private final SubjectTypeService subjectTypeService;
    private final FormService formService;
    private final ConceptService conceptService;

    @Autowired
    public ImportService(SubjectTypeRepository subjectTypeRepository, FormMappingRepository formMappingRepository, ProgramRepository programRepository, EncounterTypeRepository encounterTypeRepository, AddressLevelTypeRepository addressLevelTypeRepository, OrganisationConfigRepository organisationConfigRepository, GroupRepository groupRepository, SubjectTypeService subjectTypeService, FormService formService, ConceptService conceptService) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.formMappingRepository = formMappingRepository;
        this.programRepository = programRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.organisationConfigRepository = organisationConfigRepository;
        this.groupRepository = groupRepository;
        this.subjectTypeService = subjectTypeService;
        this.formService = formService;
        this.conceptService = conceptService;
    }

    public HashMap<String, FormMappingInfo> getImportTypes() {
        List<FormMapping> formMappings = formMappingRepository.findAllOperational();
        Stream<FormMapping> subjectProfileFormMappings = formMappings.stream().filter(formMapping -> formMapping.getForm().getFormType() == FormType.IndividualProfile);
        HashMap<String, FormMappingInfo> uploadTypes = new HashMap<>();
        subjectProfileFormMappings.forEach(formMapping -> {
            String subjectName = formMapping.getSubjectType().getName();
            uploadTypes.put(String.format("Subject---%s", subjectName), new FormMappingInfo(String.format("%s registration", subjectName), formMapping.isEnableApproval()));
        });

        Stream<FormMapping> programEnrolmentForms = formMappings.stream().filter(formMapping -> formMapping.getForm().getFormType() == FormType.ProgramEnrolment);
        programEnrolmentForms.forEach(formMapping -> {
            String subjectTypeName = formMapping.getSubjectType().getName();
            Program program = formMapping.getProgram();
            if (program == null) return; // not defined correctly

            String programName = program.getName();
            uploadTypes.put(String.format("ProgramEnrolment---%s---%s", programName, subjectTypeName), new FormMappingInfo(String.format("%s enrolment", programName), formMapping.isEnableApproval()));
        });

        Stream<FormMapping> programEncounterForms = formMappings.stream().filter(formMapping -> FormType.ProgramEncounter.equals(formMapping.getForm().getFormType()));
        programEncounterForms.forEach(formMapping -> {
            String subjectTypeName = formMapping.getSubjectType().getName();
            EncounterType encounterType = formMapping.getEncounterType();
            if (encounterType == null) return;  // not defined correctly

            String formName = formMapping.getFormName();
            uploadTypes.put(String.format("ProgramEncounter---%s---%s", encounterType.getName(), subjectTypeName), new FormMappingInfo(String.format("%s", formName), formMapping.isEnableApproval()));
        });

        Stream<FormMapping> encounterForms = formMappings.stream().filter(formMapping -> formMapping.getForm().getFormType() == FormType.Encounter);
        encounterForms.forEach(formMapping -> {
            String subjectTypeName = formMapping.getSubjectType().getName();
            EncounterType encounterType = formMapping.getEncounterType();
            if (encounterType == null) return; // not defined correctly

            String encounterTypeName = encounterType.getName();
            String formName = formMapping.getFormName();
            uploadTypes.put(String.format("Encounter---%s---%s", encounterTypeName, subjectTypeName), new FormMappingInfo(String.format("%s", formName), formMapping.isEnableApproval()));
        });

        Stream<SubjectType.SubjectTypeProjection> groupSubjectTypes = subjectTypeRepository.findAllOperational().stream().filter(SubjectType.SubjectTypeProjection::isGroup);
        groupSubjectTypes.forEach(groupSubjectType -> {
            String groupSubjectTypeName = groupSubjectType.getName();
            uploadTypes.put(String.format("GroupMembers---%s", groupSubjectTypeName), new FormMappingInfo(String.format("%s members", groupSubjectTypeName), false));
        });

        return uploadTypes;
    }

    class FormMappingInfo {
        private String name;
        private boolean isApprovalEnabled;

        public FormMappingInfo(String name, boolean isApprovalEnabled) {
            this.name = name;
            this.isApprovalEnabled = isApprovalEnabled;
        }

        public String getName() {
            return name;
        }

        public boolean isApprovalEnabled() {
            return isApprovalEnabled;
        }
    }

    /**
     * Upload types can be
     * <p>
     * Subject---<SubjectType>
     * ProgramEnrolment---<Program>---<SubjectType>
     * ProgramEncounter---<EncounterType>---<SubjectType>
     * Encounter--<EncounterType>---<SubjectType>
     * GroupMembers---<GroupSubjectTypeName>
     *
     * @param uploadType
     * @return
     */
    public String getSampleFile(String uploadType) {
        String[] uploadSpec = uploadType.split("---");
        String response = "";

        if (uploadType.equals("usersAndCatchments")) {
            return getUsersAndCatchmentsSampleFile();
        }

        if (uploadSpec[0].equals("Subject")) {
            SubjectType subjectType = subjectTypeRepository.findByName(uploadSpec[1]);
            return getSubjectSampleFile(uploadSpec, response, subjectType);
        }

        if (uploadSpec[0].equals("ProgramEnrolment")) {
            Program program = programRepository.findByName(uploadSpec[1]);
            return getProgramEnrolmentSampleFile(uploadSpec, response, program);
        }

        if (uploadSpec[0].equals("ProgramEncounter")) {
            EncounterType encounterType = encounterTypeRepository.findByName(uploadSpec[1]);
            return getProgramEncounterSampleFile(uploadSpec, response, encounterType);
        }

        if (uploadSpec[0].equals("Encounter")) {
            EncounterType encounterType = encounterTypeRepository.findByName(uploadSpec[1]);
            return getEncounterSampleFile(uploadSpec, response, encounterType);
        }

        if (uploadSpec[0].equals("GroupMembers")) {
            return getGroupMembersSampleFile(uploadSpec, response, getSubjectType(uploadSpec[1]));
        }

        throw new UnsupportedOperationException(String.format("Sample file format for %s not supported", uploadType));
    }

    public String getLocationsSampleFile(LocationWriter.LocationUploadMode locationUploadMode, String locationHierarchy) {
        List<AddressLevelType> addressLevelTypes = null;
        if (LocationWriter.LocationUploadMode.isCreateMode(locationUploadMode)) {
            addressLevelTypes = getAddressLevelTypesForCreateModeSingleHierarchy(locationHierarchy);
        }
        List<FormElement> formElementNamesForLocationTypeFormElements = formService.getFormElementNamesForLocationTypeForms();
        StringBuilder sampleFileBuilder = addSampleFileContent(locationUploadMode, addressLevelTypes, formElementNamesForLocationTypeFormElements);
        return sampleFileBuilder.toString();
    }

    private StringBuilder addSampleFileContent(LocationWriter.LocationUploadMode locationUploadMode, List<AddressLevelType> addressLevelTypes, List<FormElement> formElementNamesForLocationTypeFormElements) {
        StringBuilder sampleFileBuilder = new StringBuilder();
        sampleFileBuilder.append(buildHeaderRowForLocations(locationUploadMode, addressLevelTypes, formElementNamesForLocationTypeFormElements));
        sampleFileBuilder.append(STRING_CONSTANT_NEW_LINE);
        sampleFileBuilder.append(buildExampleRowForLocations(locationUploadMode, addressLevelTypes, formElementNamesForLocationTypeFormElements));
        sampleFileBuilder.append(STRING_CONSTANT_NEW_LINE);
        sampleFileBuilder.append(buildDescriptionRowForLocations(locationUploadMode));
        return sampleFileBuilder;
    }

    public List<AddressLevelType> getAddressLevelTypesForCreateModeSingleHierarchy(String locationHierarchy) {
        if (!StringUtils.hasText(locationHierarchy)) {
            throw new RuntimeException(String.format("Invalid value specified for locationHierarchy: %s", locationHierarchy));
        }
        List<Long> selectedLocationHierarchy = Arrays.stream(locationHierarchy.split("\\."))
                .map(Long::parseLong).collect(Collectors.toList());
        return addressLevelTypeRepository.findAllByIsVoidedFalse().stream()
                .sorted(Comparator.comparingDouble(AddressLevelType::getLevel).reversed())
                .filter(alt -> selectedLocationHierarchy.contains(alt.getId()))
                .collect(Collectors.toList());
    }

    private String listAsSeparatedString(List<String> rowItems) {
        return rowItems.stream()
                .map(rowItem -> String.format(STRING_PLACEHOLDER_BLOCK, rowItem))
                .collect(Collectors.joining(STRING_CONSTANT_SEPARATOR));
    }

    private String buildHeaderRowForLocations(LocationWriter.LocationUploadMode locationUploadMode, List<AddressLevelType> addressLevelTypes,
                                              List<FormElement> formElementNamesForLocationTypeFormElements) {
        List<String> headers = new ArrayList<>();
        if (LocationWriter.LocationUploadMode.isCreateMode(locationUploadMode)) {
            headers.addAll(addressLevelTypes.stream().map(AddressLevelType::getName).collect(Collectors.toList()));
        } else {
            headers.add(COLUMN_NAME_LOCATION_WITH_FULL_HIERARCHY);
            headers.add(COLUMN_NAME_NEW_LOCATION_NAME);
            headers.add(COLUMN_NAME_PARENT_LOCATION_WITH_FULL_HIERARCHY);
        }
        headers.add(COLUMN_NAME_GPS_COORDINATES);
        headers.addAll(formElementNamesForLocationTypeFormElements.stream()
                .map(formElement -> formElement.getConcept().getName()).collect(Collectors.toList()));
        return listAsSeparatedString(headers);
    }

    private String buildExampleRowForLocations(LocationWriter.LocationUploadMode locationUploadMode, List<AddressLevelType> addressLevelTypes,
                                               List<FormElement> formElementNamesForLocationTypeFormElements) {
        List<String> examples = new ArrayList<>();
        if (LocationWriter.LocationUploadMode.isCreateMode(locationUploadMode)) {
            examples.addAll(addressLevelTypes.stream()
                            .map(alt -> Example + alt.getName() + STRING_CONSTANT_ONE).collect(Collectors.toList()));
        } else {
            examples.add(LOCATION_WITH_FULL_HIERARCHY_EXAMPLE);
            examples.add(NEW_LOCATION_NAME_EXAMPLE);
            examples.add(PARENT_LOCATION_WITH_FULL_HIERARCHY_EXAMPLE);
        }
        examples.add(GPS_COORDINATES_EXAMPLE);
        examples.addAll(formElementNamesForLocationTypeFormElements.stream()
                .map(fe -> ALLOWED_VALUES + conceptService.getSampleValuesForSyncConcept(fe.getConcept())).collect(Collectors.toList()));
        return listAsSeparatedString(examples);
    }

    private String buildDescriptionRowForLocations(LocationWriter.LocationUploadMode locationUploadMode) {
        List<String> descriptions = new ArrayList<>();
        if (LocationWriter.LocationUploadMode.isCreateMode(locationUploadMode)) {
            descriptions.add(ENTER_YOUR_DATA_STARTING_HERE + " " + PARENT_LOCATION_REQUIRED);
        } else {
            descriptions.add(LOCATION_WITH_FULL_HIERARCHY_DESCRIPTION);
            descriptions.add(NEW_LOCATION_NAME_DESCRIPTION);
            descriptions.add(PARENT_LOCATION_WITH_FULL_HIERARCHY_DESCRIPTION);
        }
        return listAsSeparatedString(descriptions);
    }

    private String getUsersAndCatchmentsSampleFile() {
        StringBuilder sampleFileBuilder = new StringBuilder();

        try (InputStream csvFileResourceStream = this.getClass().getResourceAsStream("/bulkuploads/sample/usersAndCatchments.csv")) {
            BufferedReader csvReader = new BufferedReader(new InputStreamReader(csvFileResourceStream));
            List<String> headersForSubjectTypesWithSyncAttributes = appendHeaderRowForUsersAndCatchments(sampleFileBuilder, csvReader);
            appendDescriptionForUsersAndCatchments(sampleFileBuilder, csvReader);
            appendSampleValuesForUsersAndCatchments(sampleFileBuilder, csvReader, headersForSubjectTypesWithSyncAttributes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sampleFileBuilder.toString();
    }

    private void appendDescriptionForUsersAndCatchments(StringBuilder sampleFileBuilder, BufferedReader csvReader) throws IOException {
        String descriptionRow = csvReader.readLine();
        List<String> allowedValuesForSubjectTypesWithSyncAttributes = subjectTypeService.constructSyncAttributeAllowedValuesForSubjectTypes();
        String syncAttributesSampleValues = String.join(",", allowedValuesForSubjectTypesWithSyncAttributes);

        descriptionRow = descriptionRow.replace("#supported_languages#", getSupportedLanguages().stream()
                .map(language -> Locale.valueOf(language).getName())
                .collect(Collectors.joining(", ", "{", "}")));
        descriptionRow = descriptionRow.replace("#all_user_groups#", getGroups().stream()
                .map(Group::getName)
                .collect(Collectors.joining(", ", "{", "}")));
        descriptionRow = allowedValuesForSubjectTypesWithSyncAttributes.isEmpty() ? descriptionRow
                : String.format("%s,%s", descriptionRow, syncAttributesSampleValues);
        sampleFileBuilder.append(STRING_CONSTANT_NEW_LINE).append(descriptionRow);
    }

    private Set<String> getSupportedLanguages() {
        Long organisationId = UserContextHolder.getUserContext().getOrganisationId();
        OrganisationConfig organisationConfig = organisationConfigRepository.findByOrganisationId(organisationId);
        return organisationConfig.getSettingsObject().getSupportedLanguages();
    }

    private List<Group> getGroups() {
        return groupRepository.findAllByIsVoidedFalse();
    }

    private void appendSampleValuesForUsersAndCatchments(StringBuilder sampleFileBuilder, BufferedReader csvReader, List<String> headersForSubjectTypesWithSyncAttributes) throws IOException {
        String toBeAppendedValuesForSyncAttributeConcepts = constructSampleSyncAttributeConceptValues(headersForSubjectTypesWithSyncAttributes.size());

        String line;
        while ((line = csvReader.readLine()) != null) {
            line = headersForSubjectTypesWithSyncAttributes.isEmpty() ? line :
                    String.format("%s,%s", line, toBeAppendedValuesForSyncAttributeConcepts);
            sampleFileBuilder.append(STRING_CONSTANT_NEW_LINE).append(line);
        }
    }

    public String constructSampleSyncAttributeConceptValues(int size) {
        String[] sampleSyncConceptValues = {"\"value1\"", "\"value2\"", "\"value1,value2\""};
        List<String> sampleValuesForSyncAttributeConcepts = Collections.nCopies(size, sampleSyncConceptValues[RANDOM.nextInt(sampleSyncConceptValues.length)]);
        return String.join(",", sampleValuesForSyncAttributeConcepts);
    }

    private List<String> appendHeaderRowForUsersAndCatchments(StringBuilder sampleFileBuilder, BufferedReader csvReader) throws IOException {
        String headerRow = csvReader.readLine();
        List<String> headersForSubjectTypesWithSyncAttributes = subjectTypeService.constructSyncAttributeHeadersForSubjectTypes();
        String syncAttributesHeader = String.join(",", headersForSubjectTypesWithSyncAttributes);
        headerRow = headersForSubjectTypesWithSyncAttributes.isEmpty() ? headerRow : headerRow + "," + syncAttributesHeader;
        sampleFileBuilder.append(headerRow);
        return headersForSubjectTypesWithSyncAttributes;
    }

    private String getEncounterSampleFile(String[] uploadSpec, String response, EncounterType encounterType) {
        response = addToResponse(response, Arrays.asList(new EncounterHeaders(encounterType).getAllHeaders()));
        FormMapping formMapping = formMappingRepository.getRequiredFormMapping(getSubjectType(uploadSpec[2]).getUuid(), null, getEncounterType(uploadSpec[1]).getUuid(), FormType.Encounter);
        return addToResponse(response, formMapping);
    }

    private String getSubjectSampleFile(String[] uploadSpec, String response, SubjectType subjectType) {
        SubjectHeaders subjectHeaders = new SubjectHeaders(subjectType);
        response = addToResponse(response, Arrays.asList(subjectHeaders.getAllHeaders()));
        response = addToResponse(response, addressLevelTypeRepository.getAllNames());
        FormMapping formMapping = formMappingRepository.getRequiredFormMapping(getSubjectType(uploadSpec[1]).getUuid(), null, null, FormType.IndividualProfile);
        return addToResponse(response, formMapping);
    }

    private String getProgramEnrolmentSampleFile(String[] uploadSpec, String response, Program program) {
        response = addToResponse(response, Arrays.asList(new ProgramEnrolmentHeaders(program).getAllHeaders()));
        FormMapping formMapping = formMappingRepository.getRequiredFormMapping(getSubjectType(uploadSpec[2]).getUuid(), getProgram(uploadSpec[1]).getUuid(), null, FormType.ProgramEnrolment);
        return addToResponse(response, formMapping);
    }

    private String getProgramEncounterSampleFile(String[] uploadSpec, String response, EncounterType encounterType) {
        response = addToResponse(response, Arrays.asList(new ProgramEncounterHeaders(encounterType).getAllHeaders()));
        FormMapping formMapping = formMappingRepository.getRequiredFormMapping(getSubjectType(uploadSpec[2]).getUuid(), null, getEncounterType(uploadSpec[1]).getUuid(), FormType.ProgramEncounter);
        return addToResponse(response, formMapping);
    }

    private String getGroupMembersSampleFile(String[] uploadSpec, String response, SubjectType subjectType) {
        if (subjectType.isHousehold()) {
            response = addToResponse(response, Arrays.asList(new HouseholdMemberHeaders(subjectType).getAllHeaders()));
        } else {
            GroupMemberHeaders groupMemberHeaders = new GroupMemberHeaders(subjectType);
            groupMemberHeaders.groupId = uploadSpec[1] + " Id";
            response = addToResponse(response, Arrays.asList(groupMemberHeaders.getAllHeaders()));
        }
        return response;
    }

    private EncounterType getEncounterType(String encounterTypeName) {
        EncounterType encounterType = encounterTypeRepository.findByName(encounterTypeName);
        assertNotNull(encounterType, encounterTypeName);
        return encounterType;
    }

    private Program getProgram(String programName) {
        Program program = programRepository.findByName(programName);
        assertNotNull(program, programName);
        return program;
    }

    private SubjectType getSubjectType(String subjectTypeName) {
        SubjectType subjectType = subjectTypeRepository.findByName(subjectTypeName);
        assertNotNull(subjectType, subjectTypeName);
        return subjectType;
    }

    private void assertNotNull(Object obj, String descriptor) {
        if (obj == null) {
            String errorMessage = String.format("%s not found", descriptor);
            logger.error(errorMessage);
            throw new UnsupportedOperationException(errorMessage);
        }
    }

    private String addToResponse(String str, FormMapping formMapping) {
        assertNotNull(formMapping, "Form mapping");
        String concatenatedString = addCommaIfNecessary(str);
        List<String> conceptNames = formMapping
                .getForm()
                .getApplicableFormElements()
                .stream()
                .filter(formElement -> !ConceptDataType.isQuestionGroup(formElement.getConcept().getDataType()))
                .map(this::getHeaderName)
                .collect(Collectors.toList());
        concatenatedString = concatenatedString.concat(String.join(",", conceptNames));
        return concatenatedString;
    }

    private String getHeaderName(FormElement formElement) {
        String conceptName = formElement.getConcept().getName();
        if (formElement.getGroup() != null) {
            FormElement parentFormElement = formElement.getGroup();
            String parentChildName = parentFormElement.getConcept().getName() + "|" + conceptName;
            return parentFormElement.isRepeatable() ? String.format("\"%s|1\"", parentChildName) : String.format("\"%s\"", parentChildName);
        }
        return "\"" + conceptName + "\"";
    }

    private String addToResponse(String inputString, List<String> headers) {
        String outputString = addCommaIfNecessary(inputString);
        return outputString.concat(String.join(",", headers));
    }

    private String addCommaIfNecessary(String str) {
        if (!str.isEmpty()) {
            return str.concat(",");
        }
        return str;
    }
}
