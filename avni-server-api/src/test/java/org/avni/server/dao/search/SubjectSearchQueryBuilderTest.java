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
import java.util.regex.Pattern;

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
        assertThat(sql).contains("(case when i.observations ->>:conceptUuid0 ~ '" + BaseSubjectSearchQueryBuilder.DATE_TIME_PATTERN + "' then");
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
        assertThat(sortedSql("fullName", SortOrder.desc)).contains("order by " + BaseSubjectSearchQueryBuilder.FULL_NAME + " desc, i.id desc");
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

        assertThat(sql).contains("at time zone 'Asia/Kolkata' as date) when i.observations ->> 'c-lmp' ~ '" + BaseSubjectSearchQueryBuilder.DATE_PATTERN
                + "' then cast(i.observations ->> 'c-lmp' as date) end) as \"LMP\"");
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

        assertThat(sql).contains(" or i.id in (select penr.individual_id from program_enrolment penr where penr.is_voided is false and penr.program_exit_date_time is null and (case when cast(penr.observations as text) ilike :searchAllStored");
        assertThat(sql).doesNotContain("join program_enrolment penr on penr.individual_id = i.id");
    }

    @Test
    public void searchAllMatchesTheFullNameAsOneStringAndTypedWildcardsLiterally() {
        SqlQuery query = new SubjectSearchQueryBuilder().withSearchAll("Sunita_Test 100%").build(subjectType);

        assertThat(query.getSql()).contains("(concat_ws(' ', nullif(i.first_name, ''), nullif(i.middle_name, ''), nullif(i.last_name, '')) ilike :searchAll");
        assertThat(query.getParameters().get("searchAll")).isEqualTo("%Sunita\\_Test 100\\%%");
        assertThat(query.getParameters().get("searchAllStored")).isEqualTo("%Sunita\\_Test 100\\%%");
        assertThat(query.getSql()).contains("(case when cast(i.observations as text) ilike :searchAllStored then jsonb_path_exists(i.observations, cast(:searchAllPath as jsonpath), '{}', true) else false end)");
    }

    @Test
    public void searchAllPathMatchesTextLiterallyAndNumbersOrYesNoOnlyAsWholeValues() {
        assertThat(BaseSubjectSearchQueryBuilder.searchAllPath("a\"b\\c\nd"))
                .contains("@ like_regex \"a\\\"b\\\\c\\u000ad\" flag \"iq\"")
                .doesNotContain("number").doesNotContain("boolean");
        assertThat(BaseSubjectSearchQueryBuilder.searchAllPath("-12.50")).contains("|| (@.type() == \"number\" && @ == -12.50)");
        assertThat(BaseSubjectSearchQueryBuilder.searchAllPath("1e5")).doesNotContain("number");
        assertThat(BaseSubjectSearchQueryBuilder.searchAllPath("True")).contains("|| (@.type() == \"boolean\" && @ == true)");
    }

    @Test
    public void searchAllDropsNulCharactersBeforeBuildingItsPatterns() {
        Map<String, Object> parameters = new SubjectSearchQueryBuilder().withSearchAll("ma\u0000ngo").build(subjectType).getParameters();

        assertThat(parameters.get("searchAll")).isEqualTo("%mango%");
        assertThat(parameters.get("searchAllStored")).isEqualTo("%mango%");
        assertThat((String) parameters.get("searchAllPath")).contains("@ like_regex \"mango\" flag \"iq\"");
        assertThat(new SubjectSearchQueryBuilder().withSearchAll("\u0000").build(subjectType).getSql()).doesNotContain(":searchAll");
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

        String observationTime = "(case when i.observations ->>:conceptUuid0 ~ '" + BaseSubjectSearchQueryBuilder.TIME_PATTERN
                + "' then cast(i.observations ->>:conceptUuid0 as time) end)";
        assertThat(query.getSql()).contains(observationTime + " >= cast(:timeValueMin0 as time)")
                .contains(observationTime + " <= cast(:timeValueMax0 as time)");
        assertThat(query.getParameters()).containsEntry("conceptUuid0", "c-time");
    }

    @Test
    public void dateTimeConceptWithATimeIsComparedByTheUsersLocalDate() {
        Concept concept = new Concept("c-dt", "registration", "DateTime", null, null);
        concept.setWidget("Range");
        concept.setMinValue("2026-06-19T18:30:00.000Z");
        concept.setMaxValue("2026-06-19T18:30:00.000Z");

        String sql = new SubjectSearchQueryBuilder().withTimeZone("Asia/Kolkata")
                .withConceptsFilter(Collections.singletonList(concept)).build(subjectType).getSql();

        String observationDate = "(case when i.observations ->>:conceptUuid0 ~ '" + BaseSubjectSearchQueryBuilder.DATE_TIME_PATTERN
                + "' then cast(cast(i.observations ->>:conceptUuid0 as timestamp with time zone) at time zone 'Asia/Kolkata' as date)"
                + " when i.observations ->>:conceptUuid0 ~ '" + BaseSubjectSearchQueryBuilder.DATE_PATTERN
                + "' then cast(i.observations ->>:conceptUuid0 as date) end)";
        assertThat(sql).contains(observationDate + " >= cast(cast(:dateTimeValueMin0 as timestamp with time zone) at time zone 'Asia/Kolkata' as date)")
                .contains(observationDate + " <= cast(cast(:dateTimeValueMax0 as timestamp with time zone) at time zone 'Asia/Kolkata' as date)")
                .doesNotContain("as timestamp with time zone) >= cast(:dateTimeValueMin0 as timestamp with time zone)")
                .doesNotContain("as timestamp with time zone) <= cast(:dateTimeValueMax0 as timestamp with time zone)");
    }

    @Test
    public void dateTimeConceptWithOnlyADateIsComparedByDay() {
        Concept concept = new Concept("c-dt", "registration", "DateTime", null, null);
        concept.setWidget("Range");
        concept.setMinValue("2026-01-01");
        concept.setMaxValue("2026-03-31");

        String sql = new SubjectSearchQueryBuilder().withTimeZone("Asia/Kolkata")
                .withConceptsFilter(Collections.singletonList(concept)).build(subjectType).getSql();

        assertThat(sql).contains("then cast(i.observations ->>:conceptUuid0 as date) end) >= cast(:dateTimeValueMin0 as date)");
    }

    @Test
    public void onlyValidDateAndTimeAnswersAreCast() {
        assertThat(matches(BaseSubjectSearchQueryBuilder.DATE_TIME_PATTERN, "2026-01-15T09:00:00.000Z", "2026-06-20T14:00+05:30",
                "2024-02-29T10:00:00Z", "2026-04-30T23:59:59.999-03:00", "2026-01-15T09:00", "2026-01-15T09:00:00.000+0530",
                "2026-01-15 09:00:00", "2026-01-15t09:00:00Z", "2026-01-15T09:00:00+14:00", "2026-01-15T09:00:00-15:59", "2026-01-15T09:00:00+1559")).isTrue();
        assertThat(matches(BaseSubjectSearchQueryBuilder.DATE_TIME_PATTERN, "", "not a date", "2026-13-01T00:00:00Z", "2026-01-15",
                "15/01/2026 09:00", "2026-02-30T00:00:00Z", "2026-02-29T00:00:00Z", "2026-04-31T00:00:00Z", "2026-01-15T24:00:00Z",
                "2026-01-15T09:00:00Zjunk", "0000-01-01T00:00:00Z", "2026-01-15T09:00:00+16:00", "2026-01-15T09:00:00-16:00",
                "2026-01-15T09:00:00+23:59", "2026-01-15T09:00:00+1600")).isFalse();
        assertThat(matches(BaseSubjectSearchQueryBuilder.DATE_PATTERN, "2026-01-15", "2026-12-31", "2024-02-29", "2000-02-29", "1600-02-29",
                "2026-02-28", "2026-06-30")).isTrue();
        assertThat(matches(BaseSubjectSearchQueryBuilder.DATE_PATTERN, "", "x", "2026-00-10", "2026-01-32", "2026-01-15T00:00:00Z", "15-01-2026",
                "2026-02-30", "2026-02-29", "1900-02-29", "2100-02-29", "2026-06-31", "2026-11-31")).isFalse();
        assertThat(matches(BaseSubjectSearchQueryBuilder.TIME_PATTERN, "00:00", "09:30", "23:59", "14:15:30", "14:15:30.250",
                "9:30", "0:30", "9:30:00", " 9:30 ", "9:30 AM", "9:30:00 PM", "9:30am", "12:15 AM", "0:30 am", "09:30 pm")).isTrue();
        assertThat(matches(BaseSubjectSearchQueryBuilder.TIME_PATTERN, "", "x", "24:00", "09:60", "9.30", "930", "13:00 pm", "9:30 xm",
                "2026-01-15T09:00:00Z", "9:3")).isFalse();
    }

    private static boolean matches(String pattern, String... values) {
        Pattern compiled = Pattern.compile(pattern);
        boolean allMatch = Arrays.stream(values).allMatch(v -> compiled.matcher(v).find());
        boolean noneMatch = Arrays.stream(values).noneMatch(v -> compiled.matcher(v).find());
        if (!allMatch && !noneMatch) throw new AssertionError("Mixed result for " + Arrays.toString(values));
        return allMatch;
    }

    @Test
    public void typedTextIsMatchedLiterallyNotAsAWildcard() {
        for (String type : Arrays.asList("Text", "Id", "Notes")) {
            Concept concept = new Concept("c-text", "registration", type, null, null);
            concept.setValue("50%_off\\x");

            SqlQuery query = new SubjectSearchQueryBuilder().withConceptsFilter(Collections.singletonList(concept)).build(subjectType);

            assertThat(query.getParameters()).containsEntry("textValue0", "%50\\%\\_off\\\\x%");
            assertThat(query.getSql()).contains("i.observations->> :conceptUuid0 ilike :textValue0");
        }
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

    private static final String ENROLLED_IN_PROGRAMS = "exists (select 1 from program_enrolment pf_penr join program pf_p on pf_p.id = pf_penr.program_id where pf_penr.individual_id = i.id and pf_penr.is_voided is false and pf_p.uuid in (:programFilterUuids)";
    private static final String NOT_EXITED = " and pf_penr.program_exit_date_time is null";
    private static final String EXITED_FROM_ANY_PROGRAM = "exists (select 1 from program_enrolment pf_exit where pf_exit.individual_id = i.id and pf_exit.is_voided is false and pf_exit.program_exit_date_time is not null)";
    private static final String NOT_ENROLLED = "not exists (select 1 from program_enrolment pf_none where pf_none.individual_id = i.id and pf_none.is_voided is false)";

    private SqlQuery programSearch(List<String> programs, Boolean includeExited, Boolean notEnrolled) {
        SubjectSearchRequest request = new SubjectSearchRequest();
        request.setPrograms(programs);
        request.setIncludeExitedEnrolments(includeExited);
        request.setNotEnrolledInAnyProgram(notEnrolled);
        request.setPageElement(new PageDetails());
        return new SubjectSearchQueryBuilder().withSubjectSearchFilter(request, subjectType).build(subjectType);
    }

    @Test
    public void programFilterIsIgnoredWhenNothingIsTicked() {
        for (SqlQuery query : Arrays.asList(programSearch(null, null, null), programSearch(Collections.emptyList(), false, false))) {
            assertThat(query.getSql()).doesNotContain("pf_penr").doesNotContain("pf_none").doesNotContain("pf_exit");
            assertThat(query.getParameters()).doesNotContainKey("programFilterUuids");
        }
    }

    @Test
    public void tickedProgramsMatchOpenNonVoidedEnrolmentsOnly() {
        SqlQuery query = programSearch(Arrays.asList("p1", "p2"), null, null);
        assertThat(query.getSql()).contains("(" + ENROLLED_IN_PROGRAMS + NOT_EXITED + "))");
        assertThat(query.getSql()).doesNotContain("pf_none");
        assertThat(query.getParameters()).containsEntry("programFilterUuids", Arrays.asList("p1", "p2"));
    }

    @Test
    public void includeExitedDropsTheExitCondition() {
        SqlQuery query = programSearch(Collections.singletonList("p1"), true, false);
        assertThat(query.getSql()).contains("(" + ENROLLED_IN_PROGRAMS + "))");
        assertThat(query.getSql()).doesNotContain("program_exit_date_time");
    }

    @Test
    public void includeExitedAloneMatchesExitsFromAnyProgram() {
        for (SqlQuery query : Arrays.asList(programSearch(null, true, null), programSearch(Collections.emptyList(), true, false))) {
            assertThat(query.getSql()).contains("(" + EXITED_FROM_ANY_PROGRAM + ")");
            assertThat(query.getSql()).doesNotContain("pf_penr").doesNotContain("pf_none");
            assertThat(query.getParameters()).doesNotContainKey("programFilterUuids");
        }
    }

    @Test
    public void includeExitedWithProgramsIsLimitedToThosePrograms() {
        SqlQuery query = programSearch(Collections.singletonList("p1"), true, false);
        assertThat(query.getSql()).doesNotContain("pf_exit");
    }

    @Test
    public void includeExitedAloneWithNotEnrolledIsJoinedWithOr() {
        SqlQuery query = programSearch(null, true, true);
        assertThat(query.getSql()).contains("(" + EXITED_FROM_ANY_PROGRAM + " or " + NOT_ENROLLED + ")");
    }

    @Test
    public void notEnrolledAloneMatchesSubjectsWithNoNonVoidedEnrolment() {
        SqlQuery query = programSearch(null, null, true);
        assertThat(query.getSql()).contains("(" + NOT_ENROLLED + ")");
        assertThat(query.getSql()).doesNotContain("pf_penr");
        assertThat(query.getParameters()).doesNotContainKey("programFilterUuids");
    }

    @Test
    public void notEnrolledWithProgramsIsJoinedWithOr() {
        SqlQuery query = programSearch(Collections.singletonList("p1"), false, true);
        assertThat(query.getSql()).contains("(" + ENROLLED_IN_PROGRAMS + NOT_EXITED + ") or " + NOT_ENROLLED + ")");
    }

    @Test
    public void programFilterIsAndedWithOtherFilters() {
        SubjectSearchRequest request = new SubjectSearchRequest();
        request.setName("asha");
        request.setGender(Collections.singletonList("g1"));
        request.setNotEnrolledInAnyProgram(true);
        request.setPrograms(Collections.singletonList("p1"));
        request.setPageElement(new PageDetails());
        String sql = new SubjectSearchQueryBuilder().withSubjectSearchFilter(request, subjectType).build(subjectType).getSql();
        String where = sql.substring(sql.indexOf(" where "));
        assertThat(where).contains("i.subject_type_id = :subjectTypeId");
        assertThat(where).contains("gender.uuid in :genders");
        assertThat(where).contains("i.first_name ilike :subjectSearchToken0");
        assertThat(where).contains("(" + ENROLLED_IN_PROGRAMS + NOT_EXITED + ") or " + NOT_ENROLLED + ")");
        assertThat(where.split("\\nand ")).hasSize(4);
    }

    private KeyValues hiddenMarker() {
        KeyValues keyValues = new KeyValues();
        keyValues.add(new KeyValue(KeyType.hidden, true));
        return keyValues;
    }

    @Test
    public void enrolmentDateFilterLeavesOutExitedEnrolments() {
        String sql = new SubjectSearchQueryBuilder()
                .withProgramEnrolmentDateFilter(new DateRange("2026-01-01", "2026-03-31"))
                .build(subjectType).getSql();

        assertThat(sql).contains("from program_enrolment penr where penr.enrolment_date_time is not null and penr.is_voided is false and penr.program_exit_date_time is null");
    }

    @Test
    public void programEncounterDateFilterLeavesOutVisitsOfExitedEnrolments() {
        String sql = new SubjectSearchQueryBuilder()
                .withProgramEncounterDateFilter(new DateRange("2026-04-01", "2026-04-30"))
                .build(subjectType).getSql();

        assertThat(sql).contains("penc.is_voided is false and penr.is_voided is false and penr.program_exit_date_time is null");
    }

    @Test
    public void enrolmentAndProgramEncounterConceptFiltersLeaveOutExitedEnrolments() {
        String enrolmentSql = new SubjectSearchQueryBuilder()
                .withConceptsFilter(Collections.singletonList(new Concept("c-notes", "programEnrolment", "Notes", null, "three")))
                .build(subjectType).getSql();
        String visitSql = new SubjectSearchQueryBuilder()
                .withConceptsFilter(Collections.singletonList(new Concept("c-notes", "programEncounter", "Notes", null, "three")))
                .build(subjectType).getSql();

        assertThat(enrolmentSql).contains("penr.is_voided is false and penr.program_exit_date_time is null");
        assertThat(visitSql).contains("penr.is_voided is false and penr.program_exit_date_time is null");
    }

    @Test
    public void tickedProgrammeEnrolmentDateAndEnrolmentAnswerMustBeOnTheSameEnrolment() {
        String sql = new SubjectSearchQueryBuilder()
                .withConceptsFilter(Collections.singletonList(new Concept("c-notes", "programEnrolment", "Notes", null, "three")))
                .withProgramEnrolmentDateFilter(new DateRange("2026-01-01", "2026-01-31"))
                .withProgramsFilter(Collections.singletonList("p-a"), false, false)
                .build(subjectType).getSql();

        assertThat(countOf(sql, "from program_enrolment penr where")).isEqualTo(1);
        assertThat(sql.substring(sql.indexOf("from program_enrolment penr where")))
                .contains("penr.program_id in (select pf_program.id from program pf_program where pf_program.uuid in (:programFilterUuids))")
                .contains("penr.observations->> :conceptUuid0 ilike :textValue0")
                .contains("cast(penr.enrolment_date_time at time zone 'Asia/Kolkata' as date) >= cast(trim(:programEnrolmentDateMin) as date)")
                .contains("cast(penr.enrolment_date_time at time zone 'Asia/Kolkata' as date) <= cast(trim(:programEnrolmentDateMax) as date)");
    }

    @Test
    public void bothEndsOfARangeMustBeMetByTheSameEnrolment() {
        Concept weight = new Concept("c-weight", "programEnrolment", "Numeric", null, null);
        weight.setWidget("Range");
        weight.setMinValue("10");
        weight.setMaxValue("20");
        String sql = new SubjectSearchQueryBuilder().withConceptsFilter(Collections.singletonList(weight)).build(subjectType).getSql();

        assertThat(countOf(sql, "from program_enrolment penr where")).isEqualTo(1);
        assertThat(sql).contains(numericAnswer("penr") + " >= :numericValueMin0 and " + numericAnswer("penr") + " <= :numericValueMax0");
    }

    @Test
    public void bothEndsOfAVisitRangeMustBeMetByTheSameVisit() {
        Concept weight = new Concept("c-weight", "encounter", "Numeric", null, null);
        weight.setWidget("Range");
        weight.setMinValue("10");
        weight.setMaxValue("20");
        String sql = new SubjectSearchQueryBuilder()
                .withConceptsFilter(Collections.singletonList(weight))
                .withEncounterDateFilter(new DateRange("2026-01-01", "2026-01-31"))
                .build(subjectType).getSql();

        assertThat(countOf(sql, "from encounter e where")).isEqualTo(2);
        assertThat(sql).contains(numericAnswer("e") + " >= :numericValueMin0 and " + numericAnswer("e") + " <= :numericValueMax0")
                .contains("as date) >= cast(:encounterDateMin as date) and cast(e.encounter_date_time");
    }

    @Test
    public void numericFilterReadsOnlyAnswersThatAreNumbers() {
        Concept weight = new Concept("c-weight", "registration", "Numeric", null, null);
        weight.setMinValue("5");
        String sql = new SubjectSearchQueryBuilder().withConceptsFilter(Collections.singletonList(weight)).build(subjectType).getSql();

        assertThat(sql).contains(numericAnswer("i") + " = :numericValueMin0")
                .doesNotContain("cast(i.observations ->>:conceptUuid0 as numeric)");
    }

    private static String numericAnswer(String tableAlias) {
        String answer = tableAlias + ".observations ->> :conceptUuid0";
        return "(case when " + answer + " ~ '^[ \\t\\n\\r\\f\\v]*[+-]?([0-9]{1,255}(\\.[0-9]{0,255})?|\\.[0-9]{1,255})([eE][+-]?[0-9]{1,4})?[ \\t\\n\\r\\f\\v]*$' then cast(" + answer + " as numeric) end)";
    }

    private static final String TICKED_PROGRAMS = "penr.program_id in (select pf_program.id from program pf_program where pf_program.uuid in (:programFilterUuids))";

    private static SubjectSearchQueryBuilder tickedProgramA(boolean ticked) {
        return new SubjectSearchQueryBuilder().withTickedPrograms(ticked ? Collections.singletonList("p-a") : Collections.emptyList());
    }

    @Test
    public void programmeVisitDateLooksOnlyAtVisitsOfTheTickedProgrammes() {
        String ticked = tickedProgramA(true).withProgramEncounterDateFilter(new DateRange("2026-04-01", "2026-04-30"))
                .withProgramsFilter(Collections.singletonList("p-a"), false, false).build(subjectType).getSql();
        String none = tickedProgramA(false).withProgramEncounterDateFilter(new DateRange("2026-04-01", "2026-04-30")).build(subjectType).getSql();

        assertThat(programmeVisitClause(ticked)).contains(TICKED_PROGRAMS);
        assertThat(none).doesNotContain(TICKED_PROGRAMS);
    }

    @Test
    public void programmeVisitAnswerLooksOnlyAtVisitsOfTheTickedProgrammes() {
        Concept notes = new Concept("c-notes", "programEncounter", "Notes", null, "three");
        String ticked = tickedProgramA(true).withConceptsFilter(Collections.singletonList(notes)).build(subjectType).getSql();
        String none = tickedProgramA(false).withConceptsFilter(Collections.singletonList(notes)).build(subjectType).getSql();

        assertThat(programmeVisitClause(ticked)).contains(TICKED_PROGRAMS).contains("penc.observations->> :conceptUuid0 ilike :textValue0");
        assertThat(none).doesNotContain(TICKED_PROGRAMS);
    }

    @Test
    public void searchAllLooksOnlyAtEnrolmentsOfTheTickedProgrammes() {
        String ticked = tickedProgramA(true).withSearchAll("alpha").build(subjectType).getSql();
        String none = tickedProgramA(false).withSearchAll("alpha").build(subjectType).getSql();

        assertThat(ticked).contains("from program_enrolment penr where penr.is_voided is false and penr.program_exit_date_time is null and " + TICKED_PROGRAMS + " and (case when cast(penr.observations as text) ilike :searchAllStored");
        assertThat(ticked).contains(FULL_NAME_SEARCH).contains("(case when cast(i.observations as text) ilike :searchAllStored");
        assertThat(none).contains("from program_enrolment penr where penr.is_voided is false and penr.program_exit_date_time is null and (case when cast(penr.observations as text) ilike :searchAllStored")
                .doesNotContain(TICKED_PROGRAMS);
    }

    @Test
    public void searchAllLooksAtExitedEnrolmentsOnlyWithIncludeExited() {
        String withExited = new SubjectSearchQueryBuilder().withIncludeExitedEnrolments(true).withSearchAll("alpha").build(subjectType).getSql();

        assertThat(withExited).contains("from program_enrolment penr where penr.is_voided is false and (case when cast(penr.observations as text) ilike :searchAllStored")
                .doesNotContain("program_exit_date_time is null");
    }

    @Test
    public void enrolmentFiltersStillLookOnlyAtTheTickedProgrammesWhenTheyAreKnownFirst() {
        String sql = tickedProgramA(true).withProgramEnrolmentDateFilter(new DateRange("2026-01-01", "2026-01-31")).build(subjectType).getSql();

        assertThat(countOf(sql, TICKED_PROGRAMS)).isEqualTo(1);
        assertThat(sql.substring(sql.indexOf("from program_enrolment penr where"))).contains(TICKED_PROGRAMS);
    }

    private static final String FULL_NAME_SEARCH = BaseSubjectSearchQueryBuilder.FULL_NAME + " ilike :searchAll";

    private static String programmeVisitClause(String sql) {
        return sql.substring(sql.indexOf("join program_encounter penc"));
    }

    private static int countOf(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    @Test
    public void includeExitedKeepsExitedEnrolmentsInTheEnrolmentLevelFilters() {
        String sql = new SubjectSearchQueryBuilder()
                .withIncludeExitedEnrolments(true)
                .withConceptsFilter(Collections.singletonList(new Concept("c-notes", "programEnrolment", "Notes", null, "three")))
                .withProgramEnrolmentDateFilter(new DateRange("2026-02-01", "2026-02-01"))
                .withProgramEncounterDateFilter(new DateRange("2026-04-01", "2026-04-30"))
                .withProgramsFilter(Collections.singletonList("p-qa"), true, false)
                .build(subjectType).getSql();

        assertThat(sql).contains("from program_enrolment penr where penr.enrolment_date_time is not null and penr.is_voided is false and " + TICKED_PROGRAMS + "\n")
                .doesNotContain("program_exit_date_time is null");
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
