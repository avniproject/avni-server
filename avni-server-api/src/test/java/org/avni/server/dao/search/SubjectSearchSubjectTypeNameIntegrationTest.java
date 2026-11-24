package org.avni.server.dao.search;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.SubjectSearchRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.web.request.webapp.search.PageDetails;
import org.avni.server.web.request.webapp.search.SortOrder;
import org.avni.server.web.request.webapp.search.SubjectSearchRequest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * avni-security#97: subject search pasted the subject type's name into its SQL as a string literal,
 * and runs with row-level security off. A name with an apostrophe broke search; a crafted name ran
 * as part of the query. The name must reach the database as a bound value, never as query text.
 */
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class SubjectSearchSubjectTypeNameIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private SubjectSearchRepository subjectSearchRepository;

    private TestDataSetupService.TestCatchmentData catchment;

    private SubjectType subjectTypeNamed(String suffix, String name) {
        TestDataSetupService.TestOrganisationData org = testDataSetupService.setupOrganisation(suffix);
        setUser(org.getUser());
        catchment = testDataSetupService.setupACatchment();
        return testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setName(name).build());
    }

    private Individual subject(SubjectType subjectType, String firstName) {
        return testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType)
                .withLocation(catchment.getAddressLevel1()).withFirstName(firstName).build());
    }

    private SubjectSearchRequest request(SubjectType subjectType) {
        SubjectSearchRequest request = new SubjectSearchRequest();
        request.setSubjectType(subjectType.getUuid());
        request.setIncludeVoided(false);
        PageDetails page = new PageDetails();
        page.setPageNumber(0);
        page.setNumberOfRecordPerPage(10);
        page.setSortColumn("fullName");
        page.setSortOrder(SortOrder.asc);
        request.setPageElement(page);
        return request;
    }

    @Test
    public void aSubjectTypeNameWithAnApostropheIsSearchable() {
        SubjectType mothers = subjectTypeNamed("sec97a", "Mother's");
        Individual asha = subject(mothers, "Asha");

        List<Map<String, Object>> rows = subjectSearchRepository.search(request(mothers), new SubjectSearchQueryBuilder());
        Long count = subjectSearchRepository.getTotalCount(request(mothers), new SubjectSearchQueryBuilder());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("uuid")).isEqualTo(asha.getUuid());
        assertThat(rows.get(0).get("subjectTypeName")).isEqualTo("Mother's");
        assertThat(count).isEqualTo(1L);
    }

    @Test
    public void aCraftedSubjectTypeNameDoesNotRunAsPartOfTheQuery() {
        String crafted = "x', (select count(*) from individual) as \"leak\", '";
        SubjectType craftedType = subjectTypeNamed("sec97b", crafted);
        subject(craftedType, "Ravi");

        List<Map<String, Object>> rows = subjectSearchRepository.search(request(craftedType), new SubjectSearchQueryBuilder());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).doesNotContainKey("leak");
        assertThat(rows.get(0).get("subjectTypeName")).isEqualTo(crafted);
    }

    @Test
    public void aSubjectTypeNameHoldingAQueryMarkerComesBackUnchanged() {
        String name = "Mother $CUSTOM_FIELDS";
        SubjectType markerType = subjectTypeNamed("sec97c", name);
        subject(markerType, "Sita");

        List<Map<String, Object>> rows = subjectSearchRepository.search(request(markerType), new SubjectSearchQueryBuilder());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("subjectTypeName")).isEqualTo(name);
    }
}
