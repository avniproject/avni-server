package org.avni.server.exporter.v2;

import org.avni.server.application.FormType;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.exporter.ExportJobService;
import org.avni.server.service.AddressLevelService;
import org.avni.server.service.FormMappingService;
import org.avni.server.web.external.request.export.ExportOutput;
import org.avni.server.web.request.ExportOutputBuilder;
import org.bouncycastle.util.Strings;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.TimeZone;

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

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test
    public void extractIndividualWithoutObservations() {
        ExportV2CSVFieldExtractor exportV2CSVFieldExtractor = new ExportV2CSVFieldExtractor(exportJobParametersRepository, encounterRepository, programEncounterRepository, formMappingService, "st1", "", subjectTypeRepository, addressLevelService, programRepository, encounterTypeRepository, exportJobService);

        ExportOutput exportOutput = new ExportOutputBuilder().build();
        SubjectType subjectType = new SubjectTypeBuilder().withUuid("st1").withName("ST1").build();
        ExportJobParameters exportJobParameters = new ExportJobParametersBuilder().withTimezone(TimeZone.getDefault().getDisplayName()).build();
        User user = new UserBuilder().build();
        Individual individual = new SubjectBuilder().withSubjectType(subjectType).withAuditUser(user).withUUID("s1").build();
        LongitudinalExportItemRow longitudinalExportItemRow = new LongitudinalExportItemRowBuilder().withSubject(individual).build();


        when(addressLevelService.getAllAddressLevelTypeNames()).thenReturn(Arrays.asList("State", "District", "Block"));
        when(exportJobParametersRepository.findByUuid("st1")).thenReturn(exportJobParameters);
        when(exportJobService.getExportOutput(any())).thenReturn(exportOutput);
        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, null, FormType.IndividualProfile)).thenReturn(new LinkedHashMap<>());
        exportV2CSVFieldExtractor.init();
        String header = exportV2CSVFieldExtractor.getHeader();
        Object[] extract = exportV2CSVFieldExtractor.extract(longitudinalExportItemRow);
        assertEquals("s1", getExtractValue(header, "ST1_uuid", extract));
    }

    private Object getExtractValue(String header, String value, Object[] extract) {
        String[] headerFields = Strings.split(header, ',');
        for (int i = 0; i < headerFields.length; i++) {
            if (headerFields[i].equals(value))
                return extract[i];
        }
        return null;
    }
}
