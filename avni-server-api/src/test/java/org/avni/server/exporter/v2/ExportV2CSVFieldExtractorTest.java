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
import org.bouncycastle.util.Strings;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

    private ExportV2CSVFieldExtractor exportV2CSVFieldExtractor;
    private ExportJobParameters exportJobParameters;
    private ExportOutput exportOutput;

    @Before
    public void setup() {
        initMocks(this);

        exportV2CSVFieldExtractor = new ExportV2CSVFieldExtractor(encounterRepository, programEncounterRepository, formMappingService, "st1", subjectTypeRepository, addressLevelService, programRepository, encounterTypeRepository, exportJobService, observationService, exportJobParametersRepository);
        exportOutput = new ExportOutputBuilder().build();
        exportJobParameters = new ExportJobParametersBuilder().withTimezone(TimeZone.getDefault().getDisplayName()).build();
        when(exportJobService.getExportOutput(any())).thenReturn(exportOutput);
    }

    @Test
    public void extractIndividualWithoutObservations() throws IOException {
        User user = new UserBuilder().build();
        exportOutput.setUuid("st1");
        SubjectType subjectType = new SubjectTypeBuilder().withUuid("st1").withName("ST1").build();
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

        assertEquals("s1", getExtractValue(header, "ST1_uuid", extract));
    }

    @Test
    public void extractIndividualWithQuestionGroup() throws IOException {
        User user = new UserBuilder().build();
        exportOutput.setUuid("st1");
        SubjectType subjectType = new SubjectTypeBuilder().withUuid("st1").withName("ST1").build();
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

        assertEquals("s1", getExtractValue(header, "ST1_uuid", extract));
        assertEquals("\"2\"", getExtractValue(header, "\"ST1_C1_C2\"", extract));
    }

    @Test
    public void extractIndividualWithRepeatableQuestionGroup() throws IOException {
        User user = new UserBuilder().build();
        exportOutput.setUuid("st1");
        SubjectType subjectType = new SubjectTypeBuilder().withUuid("st1").withName("ST1").build();
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
        when(observationService.getMaxNumberOfQuestionGroupObservations(any())).thenReturn(new HashMap<FormElement, Integer>() {{
            put(qgElement, 2);
        }});

        exportV2CSVFieldExtractor.init();
        StringBuilderWriter writer = new StringBuilderWriter();
        exportV2CSVFieldExtractor.writeHeader(writer);
        String header = writer.toString();
        Object[] extract = exportV2CSVFieldExtractor.extract(longitudinalExportItemRow);

        assertEquals("s1", getExtractValue(header, "ST1_uuid", extract));
        assertEquals("\"21\"", getExtractValue(header, "\"ST1_C1_1_C2\"", extract));
        assertEquals("\"31\"", getExtractValue(header, "\"ST1_C1_1_C3\"", extract));
        assertEquals("\"22\"", getExtractValue(header, "\"ST1_C1_2_C2\"", extract));
        assertEquals("\"32\"", getExtractValue(header, "\"ST1_C1_2_C3\"", extract));
    }

    private Object getExtractValue(String header, String headerFieldName, Object[] extract) {
        String[] headerFields = getHeaderFields(header);
        for (int i = 0; i < headerFields.length; i++) {
            if (headerFields[i].equals(headerFieldName))
                return extract[i];
        }
        return null;
    }

    private String[] getHeaderFields(String header) {
        return Strings.split(header, ',');
    }
}
