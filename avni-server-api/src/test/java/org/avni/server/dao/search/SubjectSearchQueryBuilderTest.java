package org.avni.server.dao.search;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.web.request.webapp.search.Concept;
import org.avni.server.web.request.webapp.search.DateRange;
import org.avni.server.web.request.webapp.search.IntegerRange;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class SubjectSearchQueryBuilderTest {
    private SubjectType subjectType;

    @Before
    public void setup() {
        UserContext userContext = new UserContext();
        userContext.setOrganisation(new Organisation());
        UserContextHolder.create(userContext);
        subjectType = new SubjectTypeBuilder().setId(1l).setName("Individual").build();
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
}
