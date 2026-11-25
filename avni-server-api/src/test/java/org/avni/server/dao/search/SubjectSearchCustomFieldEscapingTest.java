package org.avni.server.dao.search;

import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.framework.ApplicationContextProvider;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A concept name is interpolated into the subject-search SQL as a column alias (as "..."), so a
 * name containing a double-quote could break out and inject SQL (confirmed cross-org read on
 * staging). addCustomFields must escape the identifier by doubling embedded quotes.
 */
public class SubjectSearchCustomFieldEscapingTest {
    private ApplicationContext previousContext;
    private ConceptRepository conceptRepository;
    private OrganisationConfigService organisationConfigService;

    @Before
    public void setup() {
        UserContext userContext = new UserContext();
        userContext.setOrganisation(new Organisation());
        UserContextHolder.create(userContext);

        previousContext = ApplicationContextProvider.getContext();
        ApplicationContext context = mock(ApplicationContext.class);
        organisationConfigService = mock(OrganisationConfigService.class);
        conceptRepository = mock(ConceptRepository.class);
        when(context.getBean(OrganisationConfigService.class)).thenReturn(organisationConfigService);
        when(context.getBean(ConceptRepository.class)).thenReturn(conceptRepository);
        new ApplicationContextProvider().setApplicationContext(context);
    }

    @After
    public void tearDown() {
        new ApplicationContextProvider().setApplicationContext(previousContext);
        UserContextHolder.clear();
    }

    private void searchResultConcepts(String... conceptUuids) {
        List<Map<String, Object>> concepts = java.util.Arrays.stream(conceptUuids)
                .map(uuid -> (Map<String, Object>) Map.of("uuid", (Object) uuid)).toList();
        when(organisationConfigService.getSettingsByKey(OrganisationConfigSettingKey.searchResultFields.toString()))
                .thenReturn(List.of(Map.of("subjectTypeUUID", "st-1", "searchResultConcepts", concepts)));
    }

    private void textConcept(String uuid, String name) {
        when(conceptRepository.findByUuid(uuid)).thenReturn(
                new ConceptBuilder().withUuid(uuid).withName(name).withDataType(ConceptDataType.Text).build());
    }

    @Test
    public void escapesADoubleQuoteInAConceptNameSoItStaysOneColumn() {
        textConcept("c-break", "inj\"test");
        searchResultConcepts("c-break");

        String sql = new SubjectSearchQueryBuilder().withCustomFields(null).build(null).getSql();

        assertThat(sql).contains("as \"inj\"\"test\"");   // quote doubled -> one identifier
        assertThat(sql).doesNotContain("as \"inj\"test\""); // not the vulnerable unescaped form
    }

    @Test
    public void doesNotLetAConceptNameInjectASecondColumn() {
        textConcept("c-payload", "x\",(select 1) as \"y");
        searchResultConcepts("c-payload");

        String sql = new SubjectSearchQueryBuilder().withCustomFields(null).build(null).getSql();

        // the whole payload is one escaped alias; no standalone injected "(select 1) as \"y\"" column
        assertThat(sql).doesNotContain("(select 1) as \"y\"");
        assertThat(sql).contains("as \"x\"\",(select 1) as \"\"y\"");
    }
}
