package org.avni.server.service.sync;

import org.avni.server.dao.*;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.service.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

public class ImportServiceTest {

    public static final String sampleColumnValuesSeparator = "\",\"";
    @Mock
    private SubjectTypeRepository subjectTypeRepository;
    @Mock
    private FormMappingRepository formMappingRepository;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private EncounterTypeRepository encounterTypeRepository;
    @Mock
    private AddressLevelTypeRepository addressLevelTypeRepository;
    @Mock
    private OrganisationConfigRepository organisationConfigRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private SubjectTypeService subjectTypeService;
    @Mock
    private FormService formService;
    @Mock
    private ConceptService conceptService;

    @Mock
    private SubjectImportService subjectImportService;

    private ImportService importService;

    @Before
    public void before() {
        initMocks(this);
        importService = new ImportService(subjectTypeRepository,formMappingRepository, programRepository, encounterTypeRepository,addressLevelTypeRepository, organisationConfigRepository, groupRepository, subjectTypeService, formService, conceptService, subjectImportService);
    }

    @Test
    public void shouldGenerateRandomSyncConceptValuesForSampleFile() {
        Assert.assertEquals(1, importService.constructSampleSyncAttributeConceptValues(1).split(sampleColumnValuesSeparator).length);
        Assert.assertEquals(2, importService.constructSampleSyncAttributeConceptValues(2).split(sampleColumnValuesSeparator).length);
        Assert.assertEquals(3, importService.constructSampleSyncAttributeConceptValues(3).split(sampleColumnValuesSeparator).length);
    }
}
