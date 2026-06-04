package org.avni.server.service;

import jakarta.persistence.EntityManager;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.domain.Organisation;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class BundleServiceTest {
    private static final Long ORG_ID = 1L;

    @Mock
    private EntityManager entityManager;
    @Mock
    private OrganisationService organisationService;
    @Mock
    private OrganisationRepository organisationRepository;
    @Mock
    private Organisation organisation;

    private BundleService bundleService;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        bundleService = new BundleService(entityManager, organisationService, organisationRepository);
        when(organisation.getId()).thenReturn(ORG_ID);
        // ZipOutputStream.close() fails on a zip with no entries, so write one dummy entry
        doAnswer(invocation -> {
            ZipOutputStream zos = invocation.getArgument(1);
            zos.putNextEntry(new ZipEntry("addressLevelTypes.json"));
            zos.closeEntry();
            return null;
        }).when(organisationService).addAddressLevelTypesJson(eq(ORG_ID), any(ZipOutputStream.class));
    }

    @Test
    public void shouldExportAttendanceTypesAfterSubjectTypesConceptsAndEncounterTypes() throws Exception {
        // With the import job's chunk size of 1, files are processed in export insertion order.
        // AttendanceTypeService.saveFromBundle resolves the subject type UUID and validateConfig
        // resolves concept and encounter type UUIDs during import, so attendanceTypes.json must be
        // exported after subjectTypes.json, encounterTypes.json and concepts.json.
        bundleService.createBundle(organisation, false);

        InOrder inOrder = inOrder(organisationService);
        inOrder.verify(organisationService).addSubjectTypesJson(eq(ORG_ID), any(ZipOutputStream.class));
        inOrder.verify(organisationService).addEncounterTypesJson(eq(organisation), any(ZipOutputStream.class));
        inOrder.verify(organisationService).addConceptsJson(eq(ORG_ID), any(ZipOutputStream.class));
        inOrder.verify(organisationService).addAttendanceTypesJson(eq(ORG_ID), any(ZipOutputStream.class));
        inOrder.verify(organisationService).addFormsJson(eq(ORG_ID), any(ZipOutputStream.class));
    }
}
