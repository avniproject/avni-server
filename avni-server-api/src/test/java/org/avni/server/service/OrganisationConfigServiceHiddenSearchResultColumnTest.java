package org.avni.server.service;

import org.avni.server.application.KeyType;
import org.avni.server.application.KeyValue;
import org.avni.server.application.KeyValues;
import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.data.projection.ProjectionFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * A hidden concept (avniproject/avni-product#1905) configured as a subject search result column must not
 * reach the browser or the phone as a column, while the stored setting is left exactly as configured.
 */
public class OrganisationConfigServiceHiddenSearchResultColumnTest {
    private static final long ORGANISATION_ID = 7L;

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
    private Concept hidden;
    private Concept seen;

    @Before
    public void setUp() {
        initMocks(this);
        service = new OrganisationConfigService(organisationConfigRepository, projectionFactory,
                conceptRepository, locationHierarchyService, subjectTypeRepository);
        when(subjectTypeRepository.findAllOperational()).thenReturn(Collections.emptyList());
        hidden = concept("c-verdict", "AI verdict", hiddenMarker());
        seen = concept("c-seen", "Seen answer", null);
        when(conceptRepository.getAllConceptByUuidIn(anyList())).thenReturn(Arrays.asList(hidden, seen));

        UserContext userContext = new UserContext();
        Organisation organisation = new Organisation();
        organisation.setId(ORGANISATION_ID);
        userContext.setOrganisation(organisation);
        UserContextHolder.create(userContext);
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    public void webOrganisationConfigLeavesOutTheHiddenColumnAndKeepsTheRest() {
        OrganisationConfig config = configWithColumns(hidden, seen);
        when(organisationConfigRepository.findByOrganisationId(ORGANISATION_ID)).thenReturn(config);

        JsonObject served = (JsonObject) service.getOrganisationSettings(ORGANISATION_ID).get("organisationConfig");

        assertThat(columnUuids(served)).containsExactly("c-seen");
        assertThat(columnDisplayOrders(served)).containsExactly(2);
        assertThat(served.get("languages")).isEqualTo(config.getSettings().get("languages"));
        assertThat(subjectTypeUuid(served)).isEqualTo("st-uuid");
    }

    @Test
    public void theStoredSettingIsNotChanged() {
        OrganisationConfig config = configWithColumns(hidden, seen);
        String before = config.getSettings().toString();
        when(organisationConfigRepository.findByOrganisationId(ORGANISATION_ID)).thenReturn(config);

        service.getOrganisationSettings(ORGANISATION_ID);
        service.withoutHiddenColumnsAndFilters(config.getSettings());

        assertThat(config.getSettings().toString()).isEqualTo(before);
        assertThat(columnUuids(config.getSettings())).containsExactly("c-verdict", "c-seen");
    }

    @Test
    public void settingsWithNoColumnsConfiguredComeBackAsTheyAre() {
        JsonObject settings = new JsonObject().with("languages", new String[]{"en"});

        assertThat(service.withoutHiddenColumnsAndFilters(settings)).isSameAs(settings);
        assertThat(service.withoutHiddenColumnsAndFilters(null)).isNull();
    }

    @Test
    public void columnsWhoseConceptsAreNotHiddenComeBackAsTheyAre() {
        when(conceptRepository.getAllConceptByUuidIn(anyList())).thenReturn(Collections.singletonList(seen));
        JsonObject settings = configWithColumns(seen).getSettings();

        assertThat(service.withoutHiddenColumnsAndFilters(settings)).isSameAs(settings);
    }

    @Test
    public void aColumnWhoseConceptCannotBeFoundIsKept() {
        when(conceptRepository.getAllConceptByUuidIn(anyList())).thenReturn(Collections.singletonList(hidden));
        Concept missing = concept("c-missing", "Deleted since", null);
        JsonObject settings = configWithColumns(hidden, missing).getSettings();

        assertThat(columnUuids(service.withoutHiddenColumnsAndFilters(settings))).containsExactly("c-missing");
    }

    @Test
    public void savingAConceptConfiguredAsAColumnMarksTheConfigModified() {
        OrganisationConfig config = configWithColumns(hidden, seen);
        DateTime before = DateTime.now().minusHours(1);
        config.setLastModifiedDateTime(before);
        when(organisationConfigRepository.findByOrganisationId(ORGANISATION_ID)).thenReturn(config);

        service.markModifiedIfConfiguredAsColumnOrFilter(Arrays.asList("c-other", "c-verdict"));

        assertThat(config.getLastModifiedDateTime().isAfter(before)).isTrue();
        verify(organisationConfigRepository).save(config);
    }

    @Test
    public void savingAConceptThatIsNotAColumnLeavesTheConfigAlone() {
        OrganisationConfig config = configWithColumns(hidden, seen);
        when(organisationConfigRepository.findByOrganisationId(ORGANISATION_ID)).thenReturn(config);

        service.markModifiedIfConfiguredAsColumnOrFilter(Collections.singletonList("c-other"));

        verify(organisationConfigRepository, never()).save(config);
    }

    private Concept concept(String uuid, String name, KeyValues keyValues) {
        return new ConceptBuilder().withUuid(uuid).withName(name).withDataType(ConceptDataType.Text)
                .withKeyValues(keyValues).build();
    }

    private KeyValues hiddenMarker() {
        KeyValues keyValues = new KeyValues();
        keyValues.add(new KeyValue(KeyType.hidden, true));
        return keyValues;
    }

    // The setting as the app designer stores it: one entry per subject type, concepts in display order.
    private OrganisationConfig configWithColumns(Concept... concepts) {
        List<Map<String, Object>> resultConcepts = new ArrayList<>();
        for (int i = 0; i < concepts.length; i++) {
            Map<String, Object> resultConcept = new LinkedHashMap<>();
            resultConcept.put("uuid", concepts[i].getUuid());
            resultConcept.put("name", concepts[i].getName());
            resultConcept.put("displayOrder", i + 1);
            resultConcepts.add(resultConcept);
        }
        Map<String, Object> searchField = new LinkedHashMap<>();
        searchField.put("subjectTypeUUID", "st-uuid");
        searchField.put("subjectTypeName", "Individual");
        searchField.put("searchResultConcepts", resultConcepts);
        JsonObject settings = new JsonObject()
                .with("languages", new String[]{"en"})
                .with(OrganisationConfigSettingKey.searchResultFields.name(), new ArrayList<>(Collections.singletonList(searchField)));
        OrganisationConfig config = new OrganisationConfig();
        config.setSettings(settings);
        return config;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> columns(JsonObject settings) {
        List<Map<String, Object>> fields = (List<Map<String, Object>>) settings.get(OrganisationConfigSettingKey.searchResultFields.name());
        return (List<Map<String, Object>>) fields.get(0).get("searchResultConcepts");
    }

    private List<String> columnUuids(JsonObject settings) {
        return columns(settings).stream().map(column -> (String) column.get("uuid")).collect(Collectors.toList());
    }

    private List<Integer> columnDisplayOrders(JsonObject settings) {
        return columns(settings).stream().map(column -> ((Number) column.get("displayOrder")).intValue()).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private String subjectTypeUuid(JsonObject settings) {
        List<Map<String, Object>> fields = (List<Map<String, Object>>) settings.get(OrganisationConfigSettingKey.searchResultFields.name());
        return (String) fields.get(0).get("subjectTypeUUID");
    }
}
