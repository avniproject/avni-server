package org.avni.server.web;

import org.avni.server.application.KeyType;
import org.avni.server.application.KeyValue;
import org.avni.server.application.KeyValues;
import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.ConceptService;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.web.request.ConceptContract;
import org.avni.server.web.request.webapp.ConceptExport;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.avni.server.web.response.OrganisationConfigResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.server.core.Relation;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A hidden concept (avniproject/avni-product#1905) configured as a subject search result column is left out of
 * the organisation config served to the browser and to the phone, while the stored setting is left as
 * configured. See avniproject/avni-server#1074.
 */
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class OrganisationConfigControllerIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String COLUMN_UUIDS = ".searchResultFields[0].searchResultConcepts[*].uuid";

    @Autowired
    private OrganisationConfigRepository organisationConfigRepository;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private ConceptService conceptService;
    @Autowired
    private OrganisationConfigController organisationConfigController;

    private Concept hidden;
    private Concept seen;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-admin");
        KeyValues hiddenMarker = new KeyValues();
        hiddenMarker.add(new KeyValue(KeyType.hidden, true));
        hidden = testConceptService.createConceptWithKeyValues("AI verdict", ConceptDataType.Text, hiddenMarker);
        seen = testConceptService.createConcept("Seen answer", ConceptDataType.Text);
        configureAsColumns(hidden, seen);
    }

    @Test
    public void theBrowserIsServedTheConfigWithoutTheHiddenColumn() throws Exception {
        mockMvc.perform(get("/web/organisationConfig").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organisationConfig" + COLUMN_UUIDS, contains(seen.getUuid())));
    }

    // The sync routes sit behind a request-wrapping filter whose order the test server does not reproduce,
    // so this calls the route's handler as the phone's request would reach it, and checks the one thing the
    // phone depends on besides the body: the embedded key it reads, _embedded.organisationConfig.
    @Test
    public void thePhoneSyncsTheConfigWithoutTheHiddenColumn() {
        PagedModel<EntityModel<OrganisationConfigResponse>> synced = organisationConfigController.getByLastModified(
                new DateTime(0), DateTime.now().plusMinutes(1), PageRequest.of(0, 100));

        // The test database holds every organisation's config, and a direct call is not narrowed by the
        // row-level security a real request gets, so pick out this organisation's row.
        String storedUuid = storedConfig().getUuid();
        OrganisationConfigResponse config = synced.getContent().stream()
                .map(EntityModel::getContent)
                .filter(response -> storedUuid.equals(response.getUuid()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("this organisation's config was not served"));
        assertThat(config.getLastModifiedDateTime()).isNotNull();
        assertThat(columnUuids(config.getSettings())).containsExactly(seen.getUuid());
        assertThat(OrganisationConfigResponse.class.getAnnotation(Relation.class).collectionRelation())
                .as("the phone reads _embedded.organisationConfig").isEqualTo("organisationConfig");
    }

    @Test
    public void theStoredSettingStillListsTheHiddenColumn() throws Exception {
        mockMvc.perform(get("/web/organisationConfig").accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk());

        OrganisationConfig stored = storedConfig();
        assertThat(columnUuids(stored.getSettings())).containsExactly(hidden.getUuid(), seen.getUuid());
        // What GET /organisationConfig serves to the admin screens that read and write the setting back.
        assertThat(columnUuids(stored.getSettingsForSerialization())).containsExactly(hidden.getUuid(), seen.getUuid());
    }

    @Test
    public void savingAConceptThatIsAColumnMarksTheConfigModifiedSoThePhoneReSyncsIt() throws Exception {
        DateTime before = storedConfig().getLastModifiedDateTime();
        Thread.sleep(20);

        conceptService.saveOrUpdateConcepts(List.of(contractFor(hidden)), ConceptContract.RequestType.Bundle);

        assertThat(storedConfig().getLastModifiedDateTime().isAfter(before)).isTrue();
    }

    @Test
    public void savingAConceptThatIsNotAColumnLeavesTheConfigAlone() throws Exception {
        Concept unrelated = testConceptService.createConcept("Unrelated", ConceptDataType.Text);
        DateTime before = storedConfig().getLastModifiedDateTime();
        Thread.sleep(20);

        conceptService.saveOrUpdateConcepts(List.of(contractFor(unrelated)), ConceptContract.RequestType.Bundle);

        assertThat(storedConfig().getLastModifiedDateTime()).isEqualTo(before);
    }

    private OrganisationConfig storedConfig() {
        return organisationConfigRepository.findByOrganisationId(UserContextHolder.getUserContext().getOrganisationId());
    }

    // The setting as the app designer stores it: one entry per subject type, concepts in display order.
    private void configureAsColumns(Concept... concepts) {
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
        OrganisationConfig config = storedConfig();
        config.getSettings().put(OrganisationConfigSettingKey.searchResultFields.name(), List.of(searchField));
        config.updateLastModifiedDateTime();
        organisationConfigRepository.save(config);
    }

    @SuppressWarnings("unchecked")
    private List<String> columnUuids(JsonObject settings) {
        List<Map<String, Object>> fields = (List<Map<String, Object>>) settings.get(OrganisationConfigSettingKey.searchResultFields.name());
        List<Map<String, Object>> columns = (List<Map<String, Object>>) fields.get(0).get("searchResultConcepts");
        return columns.stream().map(column -> (String) column.get("uuid")).collect(Collectors.toList());
    }

    private ConceptContract contractFor(Concept concept) throws Exception {
        String json = mapper.writeValueAsString(List.of(ConceptExport.fromConcept(concept)));
        return mapper.readValue(json, ConceptContract[].class)[0];
    }
}
