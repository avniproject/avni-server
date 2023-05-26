package org.avni.server.exporter.v2;

import org.avni.server.application.FormElement;
import org.avni.server.application.FormType;
import org.avni.server.application.TestFormElementBuilder;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
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
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;

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

        exportV2CSVFieldExtractor = new ExportV2CSVFieldExtractor(exportJobParametersRepository, encounterRepository, programEncounterRepository, formMappingService, "st1", "", subjectTypeRepository, addressLevelService, programRepository, encounterTypeRepository, exportJobService);
        exportOutput = new ExportOutputBuilder().build();
        exportJobParameters = new ExportJobParametersBuilder().withTimezone(TimeZone.getDefault().getDisplayName()).build();
        when(exportJobService.getExportOutput(any())).thenReturn(exportOutput);
    }

    @Test
    @Ignore
    public void extractIndividualWithoutObservations() {
        User user = new UserBuilder().build();
        exportOutput.setUuid("st1");
        SubjectType subjectType = new SubjectTypeBuilder().withUuid("st1").withName("ST1").build();
        Individual individual = new SubjectBuilder().withSubjectType(subjectType).withAuditUser(user).withUUID("s1").build();
        LongitudinalExportItemRow longitudinalExportItemRow = new LongitudinalExportItemRowBuilder().withSubject(individual).build();

        when(addressLevelService.getAllAddressLevelTypeNames()).thenReturn(Arrays.asList("State", "District", "Block"));
        when(exportJobParametersRepository.findByUuid("st1")).thenReturn(exportJobParameters);
        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, null, FormType.IndividualProfile)).thenReturn(new LinkedHashMap<>());

        exportV2CSVFieldExtractor.init();
        Object[] extract = exportV2CSVFieldExtractor.extract(longitudinalExportItemRow);

        assertEquals("s1", getExtractValue("", "ST1_uuid", extract));
        assertEquals(getHeaderFields("").length, extract.length);
    }

    @Test
    @Ignore
    public void extractIndividualWithQuestionGroup() {
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

        Concept concept1 = new ConceptBuilder().withName("c1").withDataType(ConceptDataType.QuestionGroup).build();
        LinkedHashMap<String, FormElement> formElementsMap = new LinkedHashMap<String, FormElement>() {{
            put("c1", new TestFormElementBuilder().withConcept(concept1).build());
        }};
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, null, FormType.IndividualProfile)).thenReturn(formElementsMap);

        exportV2CSVFieldExtractor.init();
        Object[] extract = exportV2CSVFieldExtractor.extract(longitudinalExportItemRow);

        assertEquals("s1", getExtractValue("", "ST1_uuid", extract));
        assertEquals("2", getExtractValue("", "ST1_C1_C2_1", extract));
        assertEquals(getHeaderFields("").length, extract.length + 1);
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
