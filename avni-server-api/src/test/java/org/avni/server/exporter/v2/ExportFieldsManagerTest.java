package org.avni.server.exporter.v2;

import org.avni.server.application.FormType;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.service.FormMappingService;
import org.avni.server.web.external.request.export.ExportEntityType;
import org.avni.server.web.request.ExportEntityTypeBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.TimeZone;

import static org.avni.server.exporter.v2.LongitudinalExportRequestFieldNameConstants.FIRST_NAME;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ExportFieldsManagerTest {
    @Mock
    private FormMappingService formMappingService;
    @Mock
    private EncounterRepository encounterRepository;

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test
    public void shouldAddAllStaticHeadersIfNoFieldsIsPassed() {
        ExportFieldsManager exportFieldsManager = new ExportFieldsManager(formMappingService, encounterRepository, TimeZone.getDefault().getDisplayName());
        when(formMappingService.getAllFormElementsAndDecisionMap(any(), eq(null), eq(null), eq(FormType.IndividualProfile))).thenReturn(new LinkedHashMap<>());
        ExportEntityType exportEntityType = new ExportEntityTypeBuilder().withUuid("a").build();
        exportFieldsManager.visitSubject(exportEntityType);
        List<String> coreFields = exportFieldsManager.getCoreFields(exportEntityType);
        assertTrue(coreFields.contains(FIRST_NAME));
    }
}
