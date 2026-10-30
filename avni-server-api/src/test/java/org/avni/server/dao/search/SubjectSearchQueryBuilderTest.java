package org.avni.server.dao.search;

import org.avni.server.application.KeyType;
import org.avni.server.application.KeyValue;
import org.avni.server.application.KeyValues;
import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.framework.ApplicationContextProvider;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.web.request.webapp.search.Concept;
import org.avni.server.web.request.webapp.search.DateRange;
import org.avni.server.web.request.webapp.search.IntegerRange;
import org.avni.server.web.request.webapp.search.PageDetails;
import org.avni.server.web.request.webapp.search.SortOrder;
import org.avni.server.web.request.webapp.search.SubjectSearchRequest;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class SubjectSearchQueryBuilderTest {
    private SubjectType subjectType;
    private ApplicationContext previousContext;
    private OrganisationConfigService organisationConfigService;
    private ConceptRepository conceptRepository;

    @Before
    public void setup() {
        UserContext userContext = new UserContext();
        userContext.setOrganisation(new Organisation());
        UserContextHolder.create(userContext);
        subjectType = new SubjectTypeBuilder().setId(1l).setName("Individual").build();
        previousContext = ApplicationContextProvider.getContext();
        ApplicationContext context = mock(ApplicationContext.class);
        organisationConfigService = mock(OrganisationConfigService.class);
        conceptRepository = mock(ConceptRepository.class);
        when(context.getBean(OrganisationConfigService.class)).thenReturn(organisationConfigService);
        when(context.getBean(ConceptRepository.class)).thenReturn(conceptRepository);
        when(organisationConfigService.getSettingsByKey(OrganisationConfigSettingKey.searchResultFields.toString()))
                .thenReturn(Collections.emptyList());
        new ApplicationContextProvider().setApplicationContext(context);
    }

    @Test
    public void withoutSubjectType() {
        SqlQuery query = new SubjectSearchQueryBuilder()
                .withNameFilter("name")
                .build(null);
        String sql = query.getSql();
        assertThat(sql).isNotEmpty();
        assertThat(sql).contains(SubjectSearchQueryBuilder.SubjectTypeColumn);
        assertThat(sql).contains(SubjectSearchQueryBuilder.SubjectTypeJoin);
    }

    @Test
    public void shouldBuildBaseQueryWhenRunWithoutParameters() {
        SqlQuery query = new SubjectSearchQueryBuilder().build(subjectType);
        String sql = query.getSql();
        assertThat(sql).isNotEmpty();
    }

    @Test
    public void shouldBeAbleToSearchByName() {
        SqlQuery query = new SubjectSearchQueryBuilder()
                .withNameFilter("name")
                .withSubjectTypeFilter(subjectType)
                .build(subjectType);
        String sql = query.getSql();
        System.out.println(sql);
        assertThat(sql).isNotEmpty();
        assertThat(sql).contains("i.subject_type_id = :subjectTypeId");
        assertThat(sql).contains("'Individual' as \"subjectTypeName\"");
        assertThat(sql).doesNotContain(SubjectSearchQueryBuilder.SubjectTypeColumn);
        assertThat(sql).doesNotContain(SubjectSearchQueryBuilder.SubjectTypeJoin);
    }

    @Test
    public void shouldNotAddNameFiltersForNullOrEmptyNames() {
        SqlQuery query = new SubjectSearchQueryBuilder()
                .withSubjectTypeFilter(subjectType)
                .withNameFilter("    ")
                .build(subjectType);
        assertThat(query.getSql().contains("i.last_name ilike")).isFalse();

        query = new SubjectSearchQueryBuilder()
                .withSubjectTypeFilter(subjectType)
                .withNameFilter(null)
                .build(subjectType);
        assertThat(query.getSql().contains("i.last_name ilike")).isFalse();
    }

    @Test
    public void shouldBreakNameStringIntoTokensInTheQuery() {
        SqlQuery query = new SubjectSearchQueryBuilder()
                .withSubjectTypeFilter(subjectType)
                .withNameFilter("two tokens  andAnother")
                .build(subjectType);
        assertThat(query.getParameters().containsValue("%two%")).isTrue();
        assertThat(query.getParameters().containsValue("%tokens%")).isTrue();
        assertThat(query.getParameters().containsValue("%andAnother%")).isTrue();
        assertThat(query.getParameters().size()).isEqualTo(6);
    }

    @Test
    public void shouldAddAgeFilter() {
        SqlQuery query = new SubjectSearchQueryBuilder()
                .withSubjectTypeFilter(subjectType)
                .withAgeFilter(new IntegerRange(1, null))
                .build(subjectType);
        assertThat(query.getParameters().size()).isEqualTo(4);
    }

    @Test
    public void shouldAddGenderFilter() {
        SqlQuery query = new SubjectSearchQueryBuilder()
                .withSubjectTypeFilter(subjectType)
                .withGenderFilter(null)
                .build(subjectType);
        assertThat(query.getParameters().size()).isEqualTo(3);

        ArrayList<String> genders = new ArrayList<>();
        genders.add("firstGenderUuid");
        query = new SubjectSearchQueryBuilder()
                .withGenderFilter(genders)
                .build(subjectType);
        assertThat(query.getParameters().size()).isEqualTo(3);
    }

    @Test
    public void shouldAddEncounterJoinWhtnAddingEncounterDateFilter() {
        SqlQuery query = new SubjectSearchQueryBuilder()
                .withSubjectTypeFilter(subjectType)
                .withEncounterDateFilter(new DateRange("2021-01-01", "2022-01-01"))
                .build(subjectType);
        assertThat(query.getParameters().size()).isEqualTo(5);
    }

    @Test
    public void shouldNotAddTheSameJoinsMultipleTimes() {
        SqlQuery query = new SubjectSearchQueryBuilder()
                .withSubjectTypeFilter(subjectType)
                .withProgramEncounterDateFilter(new DateRange("2021-01-01", "2022-01-01"))
                .withProgramEnrolmentDateFilter(new DateRange("2021-01-01", "2022-01-01"))
                .build(subjectType);
        assertThat(query.getParameters().size()).isEqualTo(7);
    }

    @Test
    public void shouldAddConditionsForConcepts() {
        SqlQuery query = new SubjectSearchQueryBuilder()
                .withSubjectTypeFilter(subjectType)
                .withConceptsFilter(Arrays.asList(
                        new Concept[]{new Concept("uuid", "registration", "CODED", Arrays.asList(new String[]{"asdf", "qwer"}), null)}))
                .build(subjectType);
    }

    @Test
    public void shouldMakeQueryForCount() {
        new SubjectSearchQueryBuilder()
                .withSubjectTypeFilter(subjectType)
                .forCount().build(subjectType);
    }

    @Test
    public void shouldAddDateOfBirthFilter() {
        SqlQuery query = new SubjectSearchQueryBuilder()
                .withSubjectTypeFilter(subjectType)
                .withDateOfBirthFilter(new LocalDate(1990, 6, 15))
                .build(subjectType);
        assertThat(query.getSql()).contains("i.date_of_birth = cast(:dateOfBirth as date)");
        assertThat(query.getParameters()).containsEntry("dateOfBirth", "1990-06-15");
    }

    @Test
    public void shouldSkipDateOfBirthFilterWhenNull() {
        SqlQuery query = new SubjectSearchQueryBuilder()
                .withSubjectTypeFilter(subjectType)
                .withDateOfBirthFilter(null)
                .build(subjectType);
        assertThat(query.getSql()).doesNotContain("i.date_of_birth = cast(:dateOfBirth as date)");
        assertThat(query.getParameters()).doesNotContainKey("dateOfBirth");
    }

    @Test
    public void effectiveAgeShouldSuppressAgeWhenDateOfBirthIsSet() {
        SubjectSearchRequest request = new SubjectSearchRequest();
        request.setDateOfBirth(new LocalDate(1990, 6, 15));
        request.setAge(new IntegerRange(35, null));

        assertThat(SubjectSearchQueryBuilder.effectiveAge(request)).isNull();
    }

    @Test
    public void effectiveAgeShouldReturnAgeWhenDateOfBirthIsNotSet() {
        SubjectSearchRequest request = new SubjectSearchRequest();
        request.setAge(new IntegerRange(35, null));

        IntegerRange effective = SubjectSearchQueryBuilder.effectiveAge(request);
        assertThat(effective).isNotNull();
        assertThat(effective.getMinValue()).isEqualTo(35);
    }

    @Test
    public void shouldNotApplyAgeFilterWhenAgeRangeIsNull() {
        // Contract that the D3 precedence (in withSubjectSearchFilter) relies on:
        // withAgeFilter(null) must not add an age predicate or bind an "age" parameter.
        SqlQuery query = new SubjectSearchQueryBuilder()
                .withSubjectTypeFilter(subjectType)
                .withAgeFilter(null)
                .build(subjectType);
        assertThat(query.getParameters()).doesNotContainKey("age");
        assertThat(query.getSql()).doesNotContain(":age");
    }

    @After
    public void restoreApplicationContext() {
        new ApplicationContextProvider().setApplicationContext(previousContext);
    }

    @Test
    public void hiddenConceptConfiguredAsAResultColumnIsLeftOutOfTheQuery() {
        configureColumns(textConcept("c-verdict", "AI verdict", hiddenMarker()),
                textConcept("c-seen", "Seen answer", null));

        String sql = new SubjectSearchQueryBuilder().withCustomFields(null).build(subjectType).getSql();

        assertThat(sql).doesNotContain("c-verdict").doesNotContain("AI verdict");
        assertThat(sql).contains("i.observations ->> 'c-seen' as \"Seen answer\"");
    }

    @Test
    public void conceptWithoutKeyValuesStaysAResultColumn() {
        configureColumns(textConcept("c-seen", "Seen answer", null));

        String sql = new SubjectSearchQueryBuilder().withCustomFields(null).build(subjectType).getSql();

        assertThat(sql).contains("i.observations ->> 'c-seen' as \"Seen answer\"");
    }

    @Test
    public void countQueryIsTheSameAsWithNoResultColumnsConfigured() {
        String withNoColumns = new SubjectSearchQueryBuilder().withCustomFields(null).forCount()
                .build(subjectType).getSql();
        configureColumns(textConcept("c-verdict", "AI verdict", hiddenMarker()));

        String withHiddenColumn = new SubjectSearchQueryBuilder().withCustomFields(null).forCount()
                .build(subjectType).getSql();

        assertThat(withHiddenColumn).isEqualTo(withNoColumns);
    }

    @Test
    public void searchLeavesTheResultColumnSettingAsConfigured() {
        List<Map<String, Object>> setting = configureColumns(textConcept("c-verdict", "AI verdict", hiddenMarker()));
        String settingBefore = setting.toString();

        new SubjectSearchQueryBuilder().withCustomFields(null).build(subjectType);

        assertThat(setting.toString()).isEqualTo(settingBefore);
        verify(organisationConfigService).getSettingsByKey(OrganisationConfigSettingKey.searchResultFields.toString());
        verifyNoMoreInteractions(organisationConfigService);
    }

    @Test
    public void organisationWithNoResultColumnsGetsTheQueryItGotBefore() {
        String sql = new SubjectSearchQueryBuilder().withCustomFields(null).build(subjectType).getSql();

        assertThat(sql).isEqualTo(new SubjectSearchQueryBuilder().build(subjectType).getSql());
    }

    @Test
    public void dateConceptFilterTakesTheDateInTheSearchersTimeZone() {
        String sql = new SubjectSearchQueryBuilder()
                .withTimeZone("America/New_York")
                .withConceptsFilter(Collections.singletonList(dateRangeConcept("2026-10-29", "2026-10-29")))
                .build(subjectType).getSql();

        assertThat(sql).contains("at time zone 'America/New_York' as date)");
        assertThat(sql).contains("(case when i.observations ->>:conceptUuid0 like '%T%' then");
    }

    @Test
    public void dateConceptFilterUsesIndianTimeWhenNoTimeZoneIsSent() {
        SubjectSearchRequest request = new SubjectSearchRequest();
        request.setConcept(Collections.singletonList(dateRangeConcept("2026-10-29", null)));
        PageDetails pageDetails = new PageDetails();
        pageDetails.setPageNumber(0);
        pageDetails.setNumberOfRecordPerPage(10);
        request.setPageElement(pageDetails);

        String sql = new SubjectSearchQueryBuilder().withSubjectSearchFilter(request, subjectType).build(subjectType).getSql();

        assertThat(sql).contains("at time zone 'Asia/Kolkata' as date)");
    }

    @Test
    public void unknownTimeZoneIsIgnored() {
        String sql = new SubjectSearchQueryBuilder()
                .withTimeZone("Asia/Kolkata' or 1=1 --")
                .withConceptsFilter(Collections.singletonList(dateRangeConcept("2026-10-29", null)))
                .build(subjectType).getSql();

        assertThat(sql).contains("at time zone 'Asia/Kolkata' as date)").doesNotContain("1=1");
    }

    @Test
    public void ageAscendingSortsYoungestFirstWithNoDateOfBirthLast() {
        assertThat(sortedSql("age", SortOrder.asc)).contains("order by i.date_of_birth desc nulls last, i.id desc");
    }

    @Test
    public void ageDescendingSortsOldestFirstWithNoDateOfBirthLast() {
        assertThat(sortedSql("age", SortOrder.desc)).contains("order by i.date_of_birth asc nulls last, i.id desc");
    }

    @Test
    public void ageSortKeepsTheRequestedPage() {
        SqlQuery query = sortedQuery("age", SortOrder.asc);
        assertThat(query.getSql()).contains("nulls last, i.id desc\noffset :offset limit :limit");
        assertThat(query.getParameters()).containsEntry("offset", 10).containsEntry("limit", 10);
    }

    @Test
    public void otherSortColumnsAreUnchanged() {
        assertThat(sortedSql("dateOfBirth", SortOrder.asc)).contains("order by i.date_of_birth asc, i.id desc");
        assertThat(sortedSql("fullName", SortOrder.desc)).contains("order by concat_ws(' ',i.first_name,i.middle_name,i.last_name) desc, i.id desc");
        assertThat(sortedSql("subjectType", SortOrder.asc)).contains("order by st.name asc, i.id desc");
        assertThat(sortedSql("gender", SortOrder.desc)).contains("order by gender.name desc, i.id desc");
    }

    @Test
    public void dateResultColumnIsShownInTheSearchersTimeZone() {
        org.avni.server.domain.Concept concept = new ConceptBuilder().withUuid("c-lmp").withName("LMP")
                .withDataType(ConceptDataType.Date).build();
        when(conceptRepository.findByUuid("c-lmp")).thenReturn(concept);
        configureColumns(concept);

        String sql = new SubjectSearchQueryBuilder().withTimeZone("Asia/Kolkata").withCustomFields(null).build(subjectType).getSql();

        assertThat(sql).contains("at time zone 'Asia/Kolkata' as date) else cast(i.observations ->> 'c-lmp' as date) end) as \"LMP\"");
    }

    @Test
    public void programEncounterDateFilterUsesTheProgramEncounterTableOfItsSubquery() {
        String sql = new SubjectSearchQueryBuilder()
                .withProgramEncounterDateFilter(new DateRange("2026-04-01", "2026-04-30"))
                .build(subjectType).getSql();

        assertThat(sql).contains("cast(penc.encounter_date_time at time zone 'Asia/Kolkata' as date) >= cast(:programEncounterDateMin as date)")
                .doesNotContain("pe.encounter_date_time");
    }

    @Test
    public void visitOnTheLastDayOfTheRangeIsIncluded() {
        String sql = new SubjectSearchQueryBuilder()
                .withTimeZone("Asia/Kolkata")
                .withEncounterDateFilter(new DateRange("2026-02-15", "2026-02-15"))
                .withProgramEnrolmentDateFilter(new DateRange("2026-03-01", "2026-03-31"))
                .build(subjectType).getSql();

        assertThat(sql).contains("cast(e.encounter_date_time at time zone 'Asia/Kolkata' as date) <= cast(:encounterDateMax as date)")
                .contains("cast(penr.enrolment_date_time at time zone 'Asia/Kolkata' as date) <= cast(trim(:programEnrolmentDateMax) as date)");
    }

    @Test
    public void searchAllAlsoLooksAtSubjectsWithoutAnEnrolment() {
        String sql = new SubjectSearchQueryBuilder().withSearchAll("alpha").build(subjectType).getSql();

        assertThat(sql).contains("(cast(i.observations as text) ilike :searchAll or i.id in (select penr.individual_id from program_enrolment penr");
        assertThat(sql).doesNotContain("join program_enrolment penr on penr.individual_id = i.id");
    }

    @Test
    public void notesConceptIsSearchedLikeText() {
        SqlQuery query = new SubjectSearchQueryBuilder()
                .withConceptsFilter(Collections.singletonList(new Concept("c-notes", "registration", "Notes", null, "three")))
                .build(subjectType);

        assertThat(query.getSql()).contains("i.observations->> :conceptUuid0 ilike :textValue0");
        assertThat(query.getParameters()).containsEntry("textValue0", "%three%");
    }

    @Test
    public void timeConceptIsComparedAsATimeOfDay() {
        Concept concept = new Concept("c-time", "registration", "Time", null, null);
        concept.setWidget("Range");
        concept.setMinValue("09:00");
        concept.setMaxValue("12:00");

        SqlQuery query = new SubjectSearchQueryBuilder().withConceptsFilter(Collections.singletonList(concept)).build(subjectType);

        assertThat(query.getSql()).contains("cast(i.observations ->>:conceptUuid0 as time) >= cast(:timeValueMin0 as time)")
                .contains("cast(i.observations ->>:conceptUuid0 as time) <= cast(:timeValueMax0 as time)");
        assertThat(query.getParameters()).containsEntry("conceptUuid0", "c-time");
    }

    @Test
    public void dateTimeConceptWithATimeIsComparedToTheMinute() {
        Concept concept = new Concept("c-dt", "registration", "DateTime", null, null);
        concept.setWidget("Range");
        concept.setMinValue("2026-01-01T03:30:00.000Z");
        concept.setMaxValue("2026-03-31T12:30:00.000Z");

        String sql = new SubjectSearchQueryBuilder().withConceptsFilter(Collections.singletonList(concept)).build(subjectType).getSql();

        assertThat(sql).contains("cast(i.observations ->>:conceptUuid0 as timestamp with time zone) >= cast(:dateTimeValueMin0 as timestamp with time zone)")
                .contains("cast(i.observations ->>:conceptUuid0 as timestamp with time zone) <= cast(:dateTimeValueMax0 as timestamp with time zone)");
    }

    @Test
    public void dateTimeConceptWithOnlyADateIsComparedByDay() {
        Concept concept = new Concept("c-dt", "registration", "DateTime", null, null);
        concept.setWidget("Range");
        concept.setMinValue("2026-01-01");
        concept.setMaxValue("2026-03-31");

        String sql = new SubjectSearchQueryBuilder().withTimeZone("Asia/Kolkata")
                .withConceptsFilter(Collections.singletonList(concept)).build(subjectType).getSql();

        assertThat(sql).contains("at time zone 'Asia/Kolkata' as date) else cast(i.observations ->>:conceptUuid0 as date) end) >= cast(:dateTimeValueMin0 as date)");
    }

    @Test
    public void unsupportedConceptTypeIsIgnoredInsteadOfBreakingTheSearch() {
        SqlQuery query = new SubjectSearchQueryBuilder()
                .withConceptsFilter(Collections.singletonList(new Concept("c-loc", "registration", "Location", null, "x")))
                .build(subjectType);

        assertThat(query.getParameters()).doesNotContainKey("conceptUuid0");
        assertThat(query.getSql()).doesNotContain("conceptUuid0");
    }

    private Concept dateRangeConcept(String minValue, String maxValue) {
        Concept concept = new Concept("c-date", "registration", "Date", null, null);
        concept.setWidget("Range");
        concept.setMinValue(minValue);
        concept.setMaxValue(maxValue);
        return concept;
    }

    private org.avni.server.domain.Concept textConcept(String uuid, String name, KeyValues keyValues) {
        org.avni.server.domain.Concept concept = new ConceptBuilder().withUuid(uuid).withName(name)
                .withDataType(ConceptDataType.Text).withKeyValues(keyValues).build();
        when(conceptRepository.findByUuid(uuid)).thenReturn(concept);
        return concept;
    }

    private String sortedSql(String sortColumn, SortOrder sortOrder) {
        return sortedQuery(sortColumn, sortOrder).getSql();
    }

    private SqlQuery sortedQuery(String sortColumn, SortOrder sortOrder) {
        SubjectSearchRequest request = new SubjectSearchRequest();
        PageDetails pageDetails = new PageDetails();
        pageDetails.setPageNumber(1);
        pageDetails.setNumberOfRecordPerPage(10);
        pageDetails.setSortColumn(sortColumn);
        pageDetails.setSortOrder(sortOrder);
        request.setPageElement(pageDetails);
        return new SubjectSearchQueryBuilder().withSubjectSearchFilter(request, subjectType).build(subjectType);
    }

    private KeyValues hiddenMarker() {
        KeyValues keyValues = new KeyValues();
        keyValues.add(new KeyValue(KeyType.hidden, true));
        return keyValues;
    }

    private List<Map<String, Object>> configureColumns(org.avni.server.domain.Concept... concepts) {
        List<Map<String, Object>> resultConcepts = new ArrayList<>();
        for (int i = 0; i < concepts.length; i++) {
            Map<String, Object> resultConcept = new HashMap<>();
            resultConcept.put("uuid", concepts[i].getUuid());
            resultConcept.put("name", concepts[i].getName());
            resultConcept.put("displayOrder", i + 1);
            resultConcepts.add(resultConcept);
        }
        Map<String, Object> searchFields = new HashMap<>();
        searchFields.put("subjectTypeUUID", "st-uuid");
        searchFields.put("subjectTypeName", "Individual");
        searchFields.put("searchResultConcepts", resultConcepts);
        List<Map<String, Object>> setting = Collections.singletonList(searchFields);
        when(organisationConfigService.getSettingsByKey(OrganisationConfigSettingKey.searchResultFields.toString()))
                .thenReturn(setting);
        return setting;
    }
}
