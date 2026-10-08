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
 * A hidden concept (avniproject/avni-product#1905) configured as a search filter or a My Dashboard filter must not
 * reach the browser or the phone as a filter, while the stored setting is left exactly as configured.
 * See avniproject/avni-server#1074.
 */
public class OrganisationConfigServiceHiddenFilterTest {
    private static final long ORGANISATION_ID = 7L;
    private static final String SEARCH_FILTERS = OrganisationConfigSettingKey.searchFilters.name();
    private static final String MY_DASHBOARD_FILTERS = OrganisationConfigSettingKey.myDashboardFilters.name();

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
    public void webOrganisationConfigLeavesOutTheHiddenFiltersAndKeepsTheRest() {
        OrganisationConfig config = configWithFilters(SEARCH_FILTERS, nameFilter(), conceptFilter(hidden), conceptFilter(seen));
        config.getSettings().put(MY_DASHBOARD_FILTERS, filters(conceptFilter(hidden), conceptFilter(seen)));
        when(organisationConfigRepository.findByOrganisationId(ORGANISATION_ID)).thenReturn(config);

        JsonObject served = (JsonObject) service.getOrganisationSettings(ORGANISATION_ID).get("organisationConfig");

        assertThat(filterTitles(served, SEARCH_FILTERS)).containsExactly("Name", "Seen answer");
        assertThat(filterTitles(served, MY_DASHBOARD_FILTERS)).containsExactly("Seen answer");
        assertThat(served.get("languages")).isEqualTo(config.getSettings().get("languages"));
    }

    @Test
    public void aFilterOnAHiddenConceptIsLeftOutWhateverElseItCarries() {
        JsonObject settings = configWithFilters(SEARCH_FILTERS, conceptFilter(seen), conceptFilter(hidden)).getSettings();

        List<Map<String, Object>> served = filters(service.withoutHiddenColumnsAndFilters(settings), SEARCH_FILTERS);

        assertThat(served).hasSize(1);
        assertThat(served.get(0)).isEqualTo(conceptFilter(seen));
    }

    @Test
    public void theStoredSettingIsNotChanged() {
        OrganisationConfig config = configWithFilters(SEARCH_FILTERS, nameFilter(), conceptFilter(hidden), conceptFilter(seen));
        config.getSettings().put(MY_DASHBOARD_FILTERS, filters(conceptFilter(hidden)));
        String before = config.getSettings().toString();
        when(organisationConfigRepository.findByOrganisationId(ORGANISATION_ID)).thenReturn(config);

        service.getOrganisationSettings(ORGANISATION_ID);
        service.withoutHiddenColumnsAndFilters(config.getSettings());

        assertThat(config.getSettings().toString()).isEqualTo(before);
        assertThat(filterTitles(config.getSettings(), SEARCH_FILTERS)).containsExactly("Name", "AI verdict", "Seen answer");
        assertThat(filterTitles(config.getSettings(), MY_DASHBOARD_FILTERS)).containsExactly("AI verdict");
    }

    @Test
    public void filtersWhoseConceptsAreNotHiddenComeBackAsTheyAre() {
        when(conceptRepository.getAllConceptByUuidIn(anyList())).thenReturn(Collections.singletonList(seen));
        JsonObject settings = configWithFilters(SEARCH_FILTERS, nameFilter(), conceptFilter(seen)).getSettings();

        assertThat(service.withoutHiddenColumnsAndFilters(settings)).isSameAs(settings);
    }

    @Test
    public void filtersThatNameNoConceptComeBackAsTheyAre() {
        JsonObject settings = configWithFilters(SEARCH_FILTERS, nameFilter()).getSettings();

        assertThat(service.withoutHiddenColumnsAndFilters(settings)).isSameAs(settings);
    }

    @Test
    public void aFilterWhoseConceptCannotBeFoundIsKept() {
        when(conceptRepository.getAllConceptByUuidIn(anyList())).thenReturn(Collections.singletonList(hidden));
        Concept missing = concept("c-missing", "Deleted since", null);
        JsonObject settings = configWithFilters(SEARCH_FILTERS, conceptFilter(hidden), conceptFilter(missing)).getSettings();

        assertThat(filterTitles(service.withoutHiddenColumnsAndFilters(settings), SEARCH_FILTERS)).containsExactly("Deleted since");
    }

    // The data entry app has long tolerated a filter setting that is not a list. A sync route must not fail on one.
    @Test
    public void aFilterSettingThatIsNotAListOfFiltersComesBackAsItIs() {
        JsonObject settings = configWithFilters(SEARCH_FILTERS, conceptFilter(hidden)).getSettings();
        settings.put(MY_DASHBOARD_FILTERS, "not a list");
        List<Object> oddEntries = new ArrayList<>(Arrays.asList("not a filter", conceptFilter(hidden)));
        JsonObject oddSettings = new JsonObject().with(SEARCH_FILTERS, oddEntries).with(MY_DASHBOARD_FILTERS, null);

        JsonObject served = service.withoutHiddenColumnsAndFilters(settings);
        JsonObject oddServed = service.withoutHiddenColumnsAndFilters(oddSettings);

        assertThat(served.get(MY_DASHBOARD_FILTERS)).isEqualTo("not a list");
        assertThat(filters(served, SEARCH_FILTERS)).isEmpty();
        assertThat(oddServed.get(SEARCH_FILTERS)).isEqualTo(Collections.singletonList("not a filter"));
        assertThat(oddServed.get(MY_DASHBOARD_FILTERS)).isNull();
    }

    @Test
    public void savingAConceptConfiguredAsASearchFilterMarksTheConfigModified() {
        assertSavingMarksTheConfigModified(configWithFilters(SEARCH_FILTERS, conceptFilter(hidden)));
    }

    @Test
    public void savingAConceptConfiguredAsAMyDashboardFilterMarksTheConfigModified() {
        assertSavingMarksTheConfigModified(configWithFilters(MY_DASHBOARD_FILTERS, conceptFilter(hidden)));
    }

    @Test
    public void savingAConceptThatIsNotAFilterLeavesTheConfigAlone() {
        OrganisationConfig config = configWithFilters(SEARCH_FILTERS, nameFilter(), conceptFilter(hidden));
        config.getSettings().put(MY_DASHBOARD_FILTERS, filters(conceptFilter(seen)));
        when(organisationConfigRepository.findByOrganisationId(ORGANISATION_ID)).thenReturn(config);

        service.markModifiedIfConfiguredAsColumnOrFilter(Collections.singletonList("c-other"));

        verify(organisationConfigRepository, never()).save(config);
    }

    private void assertSavingMarksTheConfigModified(OrganisationConfig config) {
        DateTime before = DateTime.now().minusHours(1);
        config.setLastModifiedDateTime(before);
        when(organisationConfigRepository.findByOrganisationId(ORGANISATION_ID)).thenReturn(config);

        service.markModifiedIfConfiguredAsColumnOrFilter(Arrays.asList("c-other", "c-verdict"));

        assertThat(config.getLastModifiedDateTime().isAfter(before)).isTrue();
        verify(organisationConfigRepository).save(config);
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

    // A concept filter as the app designer stores it.
    private Map<String, Object> conceptFilter(Concept concept) {
        Map<String, Object> scopeParameters = new LinkedHashMap<>();
        scopeParameters.put("programUUIDs", new ArrayList<>());
        scopeParameters.put("encounterTypeUUIDs", new ArrayList<>());
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("type", "Concept");
        filter.put("scope", "registration");
        filter.put("titleKey", concept.getName());
        filter.put("conceptName", concept.getName());
        filter.put("conceptUUID", concept.getUuid());
        filter.put("conceptDataType", "Text");
        filter.put("scopeParameters", scopeParameters);
        filter.put("subjectTypeUUID", "st-uuid");
        return filter;
    }

    // A filter on something other than a concept carries no conceptUUID.
    private Map<String, Object> nameFilter() {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("type", "Name");
        filter.put("titleKey", "Name");
        filter.put("subjectTypeUUID", "st-uuid");
        return filter;
    }

    @SafeVarargs
    private final List<Map<String, Object>> filters(Map<String, Object>... filters) {
        return new ArrayList<>(Arrays.asList(filters));
    }

    @SafeVarargs
    private final OrganisationConfig configWithFilters(String filterSetting, Map<String, Object>... filters) {
        JsonObject settings = new JsonObject()
                .with("languages", new String[]{"en"})
                .with(filterSetting, filters(filters));
        OrganisationConfig config = new OrganisationConfig();
        config.setSettings(settings);
        return config;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> filters(JsonObject settings, String filterSetting) {
        return (List<Map<String, Object>>) settings.get(filterSetting);
    }

    private List<String> filterTitles(JsonObject settings, String filterSetting) {
        return filters(settings, filterSetting).stream().map(filter -> (String) filter.get("titleKey")).collect(Collectors.toList());
    }
}
