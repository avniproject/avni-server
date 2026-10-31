package org.avni.server.exporter.v2;

import org.apache.commons.io.output.StringBuilderWriter;
import org.avni.server.application.Form;
import org.avni.server.application.FormElement;
import org.avni.server.application.FormType;
import org.avni.server.application.TestFormElementBuilder;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.domain.factory.metadata.ConceptAnswerBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.exporter.ExportJobService;
import org.avni.server.service.AddressLevelService;
import org.avni.server.service.FormMappingService;
import org.avni.server.service.ObservationService;
import org.avni.server.web.external.request.export.ExportOutput;
import org.avni.server.web.request.ExportOutputBuilder;
import org.avni.server.web.request.ExportEntityTypeBuilder;
import org.avni.server.web.external.request.export.ExportEntityType;
import org.avni.server.web.external.request.export.ExportFilters;
import org.avni.server.application.FormElementType;
import org.avni.server.util.FileUtil;
import org.bouncycastle.util.Strings;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.util.*;

import static org.avni.server.exporter.v2.LongitudinalExportRequestFieldNameConstants.UUID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ExportV2CSVFieldExtractorTest {
    @Mock
    private ExportJobParametersRepository exportJobParametersRepository;
    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private ProgramEncounterRepository programEncounterRepository;
    @Mock
    private FormMappingService formMappingService;
    @Mock
    private SubjectTypeRepository subjectTypeRepository;
    @Mock
    private AddressLevelService addressLevelService;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private EncounterTypeRepository encounterTypeRepository;
    @Mock
    private ExportJobService exportJobService;
    @Mock
    private ObservationService observationService;
    @Mock
    private IndividualRepository individualRepository;
    @Mock
    private LocationRepository locationRepository;

    private ExportV2CSVFieldExtractor exportV2CSVFieldExtractor;
    private ExportJobParameters exportJobParameters;
    private ExportOutput exportOutput;

    @Before
    public void setup() {
        initMocks(this);

        exportV2CSVFieldExtractor = new ExportV2CSVFieldExtractor(encounterRepository, programEncounterRepository, formMappingService, "st1", subjectTypeRepository, addressLevelService, programRepository, encounterTypeRepository, exportJobService, observationService, exportJobParametersRepository, individualRepository, locationRepository);
        exportOutput = new ExportOutputBuilder().build();
        exportJobParameters = new ExportJobParametersBuilder().withTimezone(TimeZone.getDefault().getDisplayName()).build();
        when(exportJobService.getExportOutput(any())).thenReturn(exportOutput);
    }

    @Test
    public void extractIndividualWithoutObservations() throws IOException {
        User user = new UserBuilder().build();
        exportOutput.setUuid("st1");
        SubjectType subjectType = new SubjectTypeBuilder().setUuid("st1").setName("ST1").build();
        Individual individual = new SubjectBuilder().withSubjectType(subjectType).withAuditUser(user).withUUID("s1").build();
        LongitudinalExportItemRow longitudinalExportItemRow = new LongitudinalExportItemRowBuilder().withSubject(individual).build();

        when(addressLevelService.getAllAddressLevelTypeNames()).thenReturn(Arrays.asList("State", "District", "Block"));
        when(exportJobParametersRepository.findByUuid("st1")).thenReturn(exportJobParameters);
        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, null, FormType.IndividualProfile)).thenReturn(new LinkedHashMap<>());
        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());

        exportV2CSVFieldExtractor.init();
        StringBuilderWriter writer = new StringBuilderWriter();
        exportV2CSVFieldExtractor.writeHeader(writer);
        String header = writer.toString();
        Object[] extract = exportV2CSVFieldExtractor.extract(longitudinalExportItemRow);
        assertHeaderLinesUpWithRow(header, extract);

        assertEquals("s1", getExtractValue(header, "ST1_uuid", extract));
    }

    @Test
    public void aSubjectAnswerIsExportedAsTheNameWithItsIdentifier() throws IOException {
        User user = new UserBuilder().build();
        exportOutput.setUuid("st1");
        SubjectType subjectType = new SubjectTypeBuilder().setUuid("st1").setName("ST1").build();
        ObservationCollection observationCollection = new ObservationCollectionBuilder().addObservation("c1", "school-uuid").build();
        Individual individual = new SubjectBuilder().withSubjectType(subjectType).withAuditUser(user).withObservations(observationCollection).withUUID("s1").build();
        LongitudinalExportItemRow longitudinalExportItemRow = new LongitudinalExportItemRowBuilder().withSubject(individual).build();

        when(addressLevelService.getAllAddressLevelTypeNames()).thenReturn(Arrays.asList("State", "District", "Block"));
        when(exportJobParametersRepository.findByUuid("st1")).thenReturn(exportJobParameters);
        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);

        exportOutput = new ExportOutputBuilder().forSubjectType("st1").withFields(Arrays.asList(UUID, "c1")).build();
        when(exportJobService.getExportOutput(any())).thenReturn(exportOutput);

        Concept schoolConcept = new ConceptBuilder().withUuid("c1").withName("School Name").withDataType(ConceptDataType.Subject).build();
        LinkedHashMap<String, FormElement> formElementsMap = new LinkedHashMap<String, FormElement>() {{
            put("c1", new TestFormElementBuilder().withConcept(schoolConcept).build());
        }};
        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, null, FormType.IndividualProfile)).thenReturn(formElementsMap);

        Individual school = new Individual();
        school.setUuid("school-uuid");
        school.setFirstName("GHS Wadagera");
        when(individualRepository.findAllByUuidIn(Collections.singletonList("school-uuid"))).thenReturn(Collections.singletonList(school));

        exportV2CSVFieldExtractor.init();
        StringBuilderWriter writer = new StringBuilderWriter();
        exportV2CSVFieldExtractor.writeHeader(writer);
        String header = writer.toString();
        Object[] extract = exportV2CSVFieldExtractor.extract(longitudinalExportItemRow);
        assertHeaderLinesUpWithRow(header, extract);

        assertEquals("\"GHS Wadagera(school-uuid)\"", getExtractValue(header, "\"ST1_School Name\"", extract));
    }

    @Test
    public void aQuoteInAConceptNameIsEscapedInTheHeaderSoItStaysInStepWithTheRow() throws IOException {
        User user = new UserBuilder().build();
        exportOutput.setUuid("st1");
        SubjectType subjectType = new SubjectTypeBuilder().setUuid("st1").setName("ST1").build();
        ObservationCollection observationCollection = new ObservationCollectionBuilder().addObservation("c1", "yes").build();
        Individual individual = new SubjectBuilder().withSubjectType(subjectType).withAuditUser(user).withObservations(observationCollection).withUUID("s1").build();
        LongitudinalExportItemRow longitudinalExportItemRow = new LongitudinalExportItemRowBuilder().withSubject(individual).build();

        when(addressLevelService.getAllAddressLevelTypeNames()).thenReturn(Arrays.asList("State", "District", "Block"));
        when(exportJobParametersRepository.findByUuid("st1")).thenReturn(exportJobParameters);
        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);

        exportOutput = new ExportOutputBuilder().forSubjectType("st1").withFields(Arrays.asList(UUID, "c1")).build();
        when(exportJobService.getExportOutput(any())).thenReturn(exportOutput);

        Concept quoted = new ConceptBuilder().withUuid("c1").withName("Child's \"nickname\"").withDataType(ConceptDataType.Text).build();
        LinkedHashMap<String, FormElement> formElementsMap = new LinkedHashMap<String, FormElement>() {{
            put("c1", new TestFormElementBuilder().withConcept(quoted).build());
        }};
        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, null, FormType.IndividualProfile)).thenReturn(formElementsMap);

        exportV2CSVFieldExtractor.init();
        StringBuilderWriter writer = new StringBuilderWriter();
        exportV2CSVFieldExtractor.writeHeader(writer);
        String header = writer.toString();

        // The quote is doubled inside the cell, so a CSV reader parses the cell back to the
        // concept name rather than splitting the header into extra fields.
        assertTrue(header.contains("\"ST1_Child's \"\"nickname\"\"\""));
        assertTrue(parseCsvFields(FileUtil.stripUtf8Bom(header)).contains("ST1_Child's \"nickname\""));
    }

    @Test
    public void aCommaOrQuoteInASubjectNameStaysInItsOwnColumn() throws IOException {
        User user = new UserBuilder().build();
        user.setName("Rao, \"Dr\" Anil");
        exportOutput.setUuid("st1");
        SubjectType subjectType = new SubjectTypeBuilder().setUuid("st1").setName("ST1").build();
        Individual individual = new SubjectBuilder().withSubjectType(subjectType).withAuditUser(user).withUUID("s1").withFirstName("A, B").build();
        individual.setLastName("Devi \"Sunita\", Jr");

        when(addressLevelService.getAllAddressLevelTypeNames()).thenReturn(Arrays.asList("State", "District", "Block"));
        when(exportJobParametersRepository.findByUuid("st1")).thenReturn(exportJobParameters);
        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);
        exportOutput = new ExportOutputBuilder().forSubjectType("st1").withFields(Arrays.asList(UUID, "firstName", "lastName", "createdBy", "registrationDate")).build();
        when(exportJobService.getExportOutput(any())).thenReturn(exportOutput);
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, null, FormType.IndividualProfile)).thenReturn(new LinkedHashMap<>());
        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());

        exportV2CSVFieldExtractor.init();
        StringBuilderWriter writer = new StringBuilderWriter();
        exportV2CSVFieldExtractor.writeHeader(writer);
        String header = writer.toString();
        Object[] extract = exportV2CSVFieldExtractor.extract(new LongitudinalExportItemRowBuilder().withSubject(individual).build());

        // what the writer does: join with commas, no quoting of its own
        String row = Arrays.stream(extract).map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        List<String> headerCells = parseCsvFields(FileUtil.stripUtf8Bom(header).replaceAll("[\\r\\n]+$", ""));
        List<String> rowCells = parseCsvFields(row);
        assertEquals("heading and row must declare the same number of columns", headerCells.size(), rowCells.size());
        assertTrue(rowCells.contains("A, B"));
        assertTrue(rowCells.contains("Devi \"Sunita\", Jr"));
        assertTrue(rowCells.contains("Rao, \"Dr\" Anil"));
        assertTrue(rowCells.contains("s1"));
    }

    @Test
    public void aQuoteInAResolvedNameIsEscapedSoTheRowDoesNotShift() throws IOException {
        User user = new UserBuilder().build();
        exportOutput.setUuid("st1");
        SubjectType subjectType = new SubjectTypeBuilder().setUuid("st1").setName("ST1").build();
        ObservationCollection observationCollection = new ObservationCollectionBuilder().addObservation("c1", "school-uuid").build();
        Individual individual = new SubjectBuilder().withSubjectType(subjectType).withAuditUser(user).withObservations(observationCollection).withUUID("s1").build();
        LongitudinalExportItemRow longitudinalExportItemRow = new LongitudinalExportItemRowBuilder().withSubject(individual).build();

        when(addressLevelService.getAllAddressLevelTypeNames()).thenReturn(Arrays.asList("State", "District", "Block"));
        when(exportJobParametersRepository.findByUuid("st1")).thenReturn(exportJobParameters);
        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);

        exportOutput = new ExportOutputBuilder().forSubjectType("st1").withFields(Arrays.asList(UUID, "c1")).build();
        when(exportJobService.getExportOutput(any())).thenReturn(exportOutput);

        Concept schoolConcept = new ConceptBuilder().withUuid("c1").withName("School Name").withDataType(ConceptDataType.Subject).build();
        LinkedHashMap<String, FormElement> formElementsMap = new LinkedHashMap<String, FormElement>() {{
            put("c1", new TestFormElementBuilder().withConcept(schoolConcept).build());
        }};
        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, null, FormType.IndividualProfile)).thenReturn(formElementsMap);

        Individual school = new Individual();
        school.setUuid("school-uuid");
        school.setFirstName("GHS \"Wadagera\"");
        when(individualRepository.findAllByUuidIn(Collections.singletonList("school-uuid"))).thenReturn(Collections.singletonList(school));

        exportV2CSVFieldExtractor.init();
        StringBuilderWriter writer = new StringBuilderWriter();
        exportV2CSVFieldExtractor.writeHeader(writer);
        String header = writer.toString();
        Object[] extract = exportV2CSVFieldExtractor.extract(longitudinalExportItemRow);
        assertHeaderLinesUpWithRow(header, extract);

        assertEquals("\"GHS \"\"Wadagera\"\"(school-uuid)\"", getExtractValue(header, "\"ST1_School Name\"", extract));
    }

    @Test
    public void extractIndividualWithQuestionGroup() throws IOException {
        User user = new UserBuilder().build();
        exportOutput.setUuid("st1");
        SubjectType subjectType = new SubjectTypeBuilder().setUuid("st1").setName("ST1").build();
        Map<String, String> qgObs = new HashMap<String, String>() {{
            put("c2", "2");
            put("c3", "3");
        }};
        ObservationCollection observationCollection = new ObservationCollectionBuilder().addObservation("c1", qgObs).build();
        Individual individual = new SubjectBuilder().withSubjectType(subjectType).withAuditUser(user).withObservations(observationCollection).withUUID("s1").build();
        LongitudinalExportItemRow longitudinalExportItemRow = new LongitudinalExportItemRowBuilder().withSubject(individual).build();

        when(addressLevelService.getAllAddressLevelTypeNames()).thenReturn(Arrays.asList("State", "District", "Block"));
        when(exportJobParametersRepository.findByUuid("st1")).thenReturn(exportJobParameters);
        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);

        exportOutput = new ExportOutputBuilder().forSubjectType("st1").withFields(Arrays.asList(UUID, "c2")).build();
        when(exportJobService.getExportOutput(any())).thenReturn(exportOutput);

        Concept concept1 = new ConceptBuilder().withUuid("c1").withName("C1").withDataType(ConceptDataType.QuestionGroup).build();
        Concept concept2 = new ConceptBuilder().withUuid("c2").withName("C2").withDataType(ConceptDataType.Text).build();
        Concept concept3 = new ConceptBuilder().withUuid("c3").withName("C3").withDataType(ConceptDataType.Text).build();
        FormElement qgElement = new TestFormElementBuilder().withConcept(concept1).build();
        LinkedHashMap<String, FormElement> formElementsMap = new LinkedHashMap<String, FormElement>() {{
            put("c1", qgElement);
            put("c2", new TestFormElementBuilder().withQuestionGroupElement(qgElement).withConcept(concept2).build());
            put("c3", new TestFormElementBuilder().withQuestionGroupElement(qgElement).withConcept(concept3).build());
        }};
        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, null, FormType.IndividualProfile)).thenReturn(formElementsMap);

        exportV2CSVFieldExtractor.init();
        StringBuilderWriter writer = new StringBuilderWriter();
        exportV2CSVFieldExtractor.writeHeader(writer);
        String header = writer.toString();
        Object[] extract = exportV2CSVFieldExtractor.extract(longitudinalExportItemRow);
        assertHeaderLinesUpWithRow(header, extract);

        assertEquals("s1", getExtractValue(header, "ST1_uuid", extract));
        assertEquals("\"2\"", getExtractValue(header, "\"ST1_C1_C2\"", extract));
    }

    @Test
    public void extractIndividualWithRepeatableQuestionGroup() throws IOException {
        User user = new UserBuilder().build();
        exportOutput.setUuid("st1");
        SubjectType subjectType = new SubjectTypeBuilder().setUuid("st1").setName("ST1").build();
        Map<String, String> qgObs1 = new HashMap<String, String>() {{
            put("c2", "21");
            put("c3", "31");
        }};
        Map<String, String> qgObs2 = new HashMap<String, String>() {{
            put("c2", "22");
            put("c3", "32");
        }};
        ObservationCollection observationCollection = new ObservationCollectionBuilder().addObservation("c1", Arrays.asList(qgObs1, qgObs2)).build();
        Individual individual = new SubjectBuilder().withSubjectType(subjectType).withAuditUser(user).withObservations(observationCollection).withUUID("s1").build();
        LongitudinalExportItemRow longitudinalExportItemRow = new LongitudinalExportItemRowBuilder().withSubject(individual).build();

        when(addressLevelService.getAllAddressLevelTypeNames()).thenReturn(Arrays.asList("State", "District", "Block"));
        when(exportJobParametersRepository.findByUuid("st1")).thenReturn(exportJobParameters);
        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);
        exportOutput = new ExportOutputBuilder().forSubjectType("st1").withFields(Arrays.asList(UUID, "c2", "c3")).build();
        when(exportJobService.getExportOutput(any())).thenReturn(exportOutput);

        Concept concept1 = new ConceptBuilder().withUuid("c1").withName("C1").withDataType(ConceptDataType.QuestionGroup).build();
        Concept concept2 = new ConceptBuilder().withUuid("c2").withName("C2").withDataType(ConceptDataType.Text).build();
        Concept concept3 = new ConceptBuilder().withUuid("c3").withName("C3").withDataType(ConceptDataType.Text).build();
        FormElement qgElement = new TestFormElementBuilder().withConcept(concept1).withRepeatable(true).build();
        LinkedHashMap<String, FormElement> formElementsMap = new LinkedHashMap<String, FormElement>() {{
            put("c1", qgElement);
            put("c2", new TestFormElementBuilder().withQuestionGroupElement(qgElement).withConcept(concept2).build());
            put("c3", new TestFormElementBuilder().withQuestionGroupElement(qgElement).withConcept(concept3).build());
        }};
        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, null, FormType.IndividualProfile)).thenReturn(formElementsMap);
        when(observationService.getMaxNumberOfQuestionGroupObservations(any(), any())).thenReturn(new HashMap<FormElement, Integer>() {{
            put(qgElement, 2);
        }});

        exportV2CSVFieldExtractor.init();
        StringBuilderWriter writer = new StringBuilderWriter();
        exportV2CSVFieldExtractor.writeHeader(writer);
        String header = writer.toString();
        Object[] extract = exportV2CSVFieldExtractor.extract(longitudinalExportItemRow);
        assertHeaderLinesUpWithRow(header, extract);

        assertEquals("s1", getExtractValue(header, "ST1_uuid", extract));
        assertEquals("\"21\"", getExtractValue(header, "\"ST1_C1_1_C2\"", extract));
        assertEquals("\"31\"", getExtractValue(header, "\"ST1_C1_1_C3\"", extract));
        assertEquals("\"22\"", getExtractValue(header, "\"ST1_C1_2_C2\"", extract));
        assertEquals("\"32\"", getExtractValue(header, "\"ST1_C1_2_C3\"", extract));
    }


    // #1075: a missing visit is padded by one visit's width, an absent visit type or programme by the heading's
    // whole block for it, and the width is what the heading writes (multi-select answers, repeatable groups).
    private static final String ET = "et1";
    private Concept multiSelect;
    private FormElement repeatableGroup;

    private void setUpSubjectWithVisitType(long maxVisits, boolean withProgram) {
        exportOutput.setUuid("st1");
        SubjectType subjectType = new SubjectTypeBuilder().setUuid("st1").setName("ST1").build();
        when(addressLevelService.getAllAddressLevelTypeNames()).thenReturn(Arrays.asList("State", "District"));
        when(exportJobParametersRepository.findByUuid("st1")).thenReturn(exportJobParameters);
        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);
        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, null, FormType.IndividualProfile)).thenReturn(new LinkedHashMap<>());

        Concept text = new ConceptBuilder().withUuid("c1").withName("C1").withDataType(ConceptDataType.Text).build();
        Concept yes = new ConceptBuilder().withUuid("a1").withName("A1").withDataType(ConceptDataType.NA).build();
        Concept no = new ConceptBuilder().withUuid("a2").withName("A2").withDataType(ConceptDataType.NA).build();
        multiSelect = new ConceptBuilder().withUuid("c2").withName("C2").withDataType(ConceptDataType.Coded).build();
        multiSelect.addAnswer(new ConceptAnswerBuilder().withConcept(multiSelect).withAnswerConcept(yes).withOrder(1).withUUID("ca1").build());
        multiSelect.addAnswer(new ConceptAnswerBuilder().withConcept(multiSelect).withAnswerConcept(no).withOrder(2).withUUID("ca2").build());
        Concept qg = new ConceptBuilder().withUuid("c3").withName("C3").withDataType(ConceptDataType.QuestionGroup).build();
        Concept qgChild = new ConceptBuilder().withUuid("c4").withName("C4").withDataType(ConceptDataType.Text).build();
        repeatableGroup = new TestFormElementBuilder().withConcept(qg).withRepeatable(true).build();
        LinkedHashMap<String, FormElement> visitForm = new LinkedHashMap<String, FormElement>() {{
            put("c1", new TestFormElementBuilder().withConcept(text).build());
            put("c2", new TestFormElementBuilder().withConcept(multiSelect).withType(FormElementType.MultiSelect.name()).build());
            put("c3", repeatableGroup);
            put("c4", new TestFormElementBuilder().withQuestionGroupElement(repeatableGroup).withConcept(qgChild).build());
        }};
        when(observationService.getMaxNumberOfQuestionGroupObservations(any(), any())).thenReturn(new HashMap<FormElement, Integer>() {{
            put(repeatableGroup, 2);
        }});
        ExportFilters filters = new ExportFilters();
        filters.setDate(new ExportFilters.DateFilter());
        List<String> visitFields = Arrays.asList(UUID, "c1", "c2", "c3", "c4");
        ExportEntityType visitType = new ExportEntityTypeBuilder().withUuid(ET).withFields(visitFields).build();
        visitType.setFilters(filters);
        when(encounterTypeRepository.findByUuid(ET)).thenReturn(new EncounterTypeBuilder().withUuid(ET).withName("ET1").build());
        when(formMappingService.findForEncounter(ET, FormType.Encounter)).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        when(formMappingService.findForEncounter(ET, FormType.ProgramEncounter)).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, ET, FormType.Encounter)).thenReturn(visitForm);
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", "p1", ET, FormType.ProgramEncounter)).thenReturn(visitForm);
        when(encounterRepository.getMaxEncounterCount(eq(ET), any(), any())).thenReturn(maxVisits);
        when(programEncounterRepository.getMaxProgramEncounterCount(eq(ET), any(), any())).thenReturn(maxVisits);

        ExportOutputBuilder builder = new ExportOutputBuilder().forSubjectType("st1").withFields(Collections.singletonList(UUID));
        if (withProgram) {
            ExportOutput.ExportNestedOutput program = new ExportOutput.ExportNestedOutput();
            program.setUuid("p1");
            program.setFields(Collections.singletonList(UUID));
            program.setFilters(filters);
            program.setEncounters(Collections.singletonList(visitType));
            when(programRepository.findByUuid("p1")).thenReturn(new ProgramBuilder().withUuid("p1").withName("P1").build());
            when(formMappingService.findForProgram("p1", FormType.ProgramEnrolment)).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
            when(formMappingService.getAllFormElementsAndDecisionMap("st1", "p1", null, FormType.ProgramEnrolment)).thenReturn(new LinkedHashMap<>());
            builder.withProgram(Collections.singletonList(program));
        } else {
            builder.withEncounterTypes(Collections.singletonList(visitType));
        }
        exportOutput = builder.build();
        when(exportJobService.getExportOutput(any())).thenReturn(exportOutput);
    }

    private LongitudinalExportItemRow subjectWithVisits(int visits) {
        SubjectType subjectType = new SubjectTypeBuilder().setUuid("st1").setName("ST1").build();
        Individual individual = new SubjectBuilder().withSubjectType(subjectType).withAuditUser(new UserBuilder().build()).withUUID("s" + visits).build();
        LongitudinalExportItemRow row = new LongitudinalExportItemRowBuilder().withSubject(individual).build();
        if (visits > 0) {
            List<Encounter> encounters = new ArrayList<>();
            for (int i = 0; i < visits; i++) {
                Encounter encounter = new Encounter();
                encounter.setUuid("e" + i);
                encounter.setObservations(new ObservationCollectionBuilder().addObservation("c1", "v" + i).build());
                encounters.add(encounter);
            }
            row.setEncounterTypeToEncountersMap(new HashMap<String, List<Encounter>>() {{ put(ET, encounters); }});
        } else {
            row.setEncounterTypeToEncountersMap(new HashMap<>());
        }
        row.setProgramEnrolmentToEncountersMap(new HashMap<>());
        row.setGroupSubjectToEncountersMap(new HashMap<>());
        return row;
    }

    private String header() throws IOException {
        exportV2CSVFieldExtractor.init();
        StringBuilderWriter writer = new StringBuilderWriter();
        exportV2CSVFieldExtractor.writeHeader(writer);
        return writer.toString();
    }

    @Test
    public void aSubjectWithFewerVisitsThanTheMostVisitedLinesUpWithTheHeading() throws IOException {
        setUpSubjectWithVisitType(3, false);
        String header = header();
        for (int visits = 0; visits <= 3; visits++) {
            assertHeaderLinesUpWithRow(header, exportV2CSVFieldExtractor.extract(subjectWithVisits(visits)));
        }
        Object[] oneVisit = exportV2CSVFieldExtractor.extract(subjectWithVisits(1));
        assertEquals("e0", getExtractValue(header, "ET1_1_uuid", oneVisit));
        assertEquals("\"v0\"", getExtractValue(header, "\"ET1_1_C1\"", oneVisit));
    }

    @Test
    public void aSubjectWithNoneOfAVisitTypeLinesUpWhenTheMaximumIsOne() throws IOException {
        setUpSubjectWithVisitType(1, false);
        String header = header();
        assertHeaderLinesUpWithRow(header, exportV2CSVFieldExtractor.extract(subjectWithVisits(0)));
        assertHeaderLinesUpWithRow(header, exportV2CSVFieldExtractor.extract(subjectWithVisits(1)));
    }

    @Test
    public void aSubjectNotEnrolledLinesUpWhenTheProgrammeHasAVisitTypeWithSeveralVisits() throws IOException {
        setUpSubjectWithVisitType(2, true);
        assertHeaderLinesUpWithRow(header(), exportV2CSVFieldExtractor.extract(subjectWithVisits(0)));
    }

    private Object getExtractValue(String header, String headerFieldName, Object[] extract) {
        String[] headerFields = getHeaderFields(header);
        for (int i = 0; i < headerFields.length; i++) {
            if (headerFields[i].equals(headerFieldName))
                return extract[i];
        }
        return null;
    }

    /**
     * A heading that declares a different number of columns from the row under it is the shape that
     * produced most of this card's findings, so every test here asserts against it.
     */
    private void assertHeaderLinesUpWithRow(String header, Object[] extract) {
        assertEquals("heading and row must declare the same number of columns",
                parseCsvFields(FileUtil.stripUtf8Bom(header)).size(), extract.length);
    }

    private java.util.List<String> parseCsvFields(String line) {
        java.util.List<String> fields = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') { current.append('"'); i++; }
                else inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) { fields.add(current.toString()); current.setLength(0); }
            else current.append(c);
        }
        fields.add(current.toString());
        return fields;
    }

    private String[] getHeaderFields(String header) {
        // The header leads with a UTF-8 BOM so Excel can tell what it is holding. Every real reader
        // of this file drops it before looking at the first column name; do the same here.
        assertTrue(header.startsWith(FileUtil.UTF8_BOM));
        return Strings.split(FileUtil.stripUtf8Bom(header), ',');
    }
}
