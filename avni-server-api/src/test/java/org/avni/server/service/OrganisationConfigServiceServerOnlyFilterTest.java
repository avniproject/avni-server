package org.avni.server.service;

import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OrganisationConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.data.projection.ProjectionFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Server-only-routing filter on the web surface (avniproject/avni-server#1012, D17): the
 * {@code storageBackends} / {@code storageTargets} keys must be absent from the
 * {@code /web/organisationConfig} payload produced by {@link OrganisationConfigService#getOrganisationSettings}.
 */
public class OrganisationConfigServiceServerOnlyFilterTest {

    @Mock
    private OrganisationConfigRepository organisationConfigRepository;
    @Mock
    private ProjectionFactory projectionFactory;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private LocationHierarchyService locationHierarchyService;
    @Mock
    private SubjectTypeRepository subjectTypeRepository;

    private OrganisationConfigService service;

    @Before
    public void setUp() {
        initMocks(this);
        service = new OrganisationConfigService(organisationConfigRepository, projectionFactory,
                conceptRepository, locationHierarchyService, subjectTypeRepository);
        when(conceptRepository.getAllConceptByUuidIn(anyList())).thenReturn(Collections.emptyList());
        when(subjectTypeRepository.findAllOperational()).thenReturn(Collections.emptyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void webOrganisationConfigOmitsServerOnlyStorageKeys() {
        Map<String, Object> backends = new LinkedHashMap<>();
        backends.put("model", "org-gcs");
        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("org-gcs", Collections.singletonMap("bucket", "org-models"));

        JsonObject settings = new JsonObject();
        settings.put("languages", new String[]{"en"});
        settings.put(OrganisationConfigSettingKey.storageBackends.name(), backends);
        settings.put(OrganisationConfigSettingKey.storageTargets.name(), targets);

        OrganisationConfig config = new OrganisationConfig();
        config.setSettings(settings);
        when(organisationConfigRepository.findByOrganisationId(1L)).thenReturn(config);

        LinkedHashMap<String, Object> response = service.getOrganisationSettings(1L);
        JsonObject webSettings = (JsonObject) response.get("organisationConfig");

        assertFalse("storageBackends must be absent from /web/organisationConfig",
                webSettings.containsKey(OrganisationConfigSettingKey.storageBackends.name()));
        assertFalse("storageTargets must be absent from /web/organisationConfig",
                webSettings.containsKey(OrganisationConfigSettingKey.storageTargets.name()));
        assertTrue("non-server-only keys must still be present", webSettings.containsKey("languages"));

        // the persisted (server-side) settings must be untouched
        assertTrue("server-side settings must retain the routing config",
                config.getSettings().containsKey(OrganisationConfigSettingKey.storageBackends.name()));
    }
}
