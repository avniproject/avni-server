package org.avni.server.service.abha;

import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AbhaHealthCentreServiceTest {
    private ExternalSystemConfigRepository repository;
    private AbhaHealthCentreService service;

    @Before
    public void setUp() throws Exception {
        repository = mock(ExternalSystemConfigRepository.class);
        service = new AbhaHealthCentreService(repository);
        ExternalSystemConfig row = new ExternalSystemConfig();
        row.setConfig(new JsonObject().with(EkaSystemConfig.HIP_IDS, Arrays.asList("hip-1", "hip-2")));
        when(repository.getEkaSystemConfig(7L)).thenReturn(new EkaSystemConfig(row));
        when(repository.getEkaSystemConfig(8L)).thenThrow(new EkaNotConfiguredException("none"));
    }

    @Test
    public void idInTheOrganisationsListIsAccepted() {
        assertTrue(service.isHealthCentreOf(7L, "hip-2"));
    }

    @Test
    public void idNotInTheListIsRefused() {
        assertFalse(service.isHealthCentreOf(7L, "hip-9"));
    }

    @Test
    public void organisationWithNoEkaSettingsRefusesAnyId() {
        assertFalse(service.isHealthCentreOf(8L, "hip-1"));
    }

    @Test
    public void nullOrganisationRefusesAnyId() {
        assertFalse(service.isHealthCentreOf(null, "hip-1"));
    }

    @Test
    public void messageNamesTheId() {
        assertEquals("ABHA Health Facility ID 'hip-9' is not one of this organisation's health centres",
                AbhaHealthCentreService.notAHealthCentreMessage("hip-9"));
    }
}
