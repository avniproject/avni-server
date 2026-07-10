package org.avni.server.rls;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Layer 2 - no-regression / forward-correctness for V1_398 (#1008).
 * Exercises the actual RLS enforcement path (the JDBC interceptor does SET ROLE "&lt;db_user&gt;" per org) to
 * assert that the new index-friendly policies preserve visibility exactly:
 *  - tx tables: a row is visible only to its own org;
 *  - ref tables: a row is visible to descendant orgs (ancestor arm) but not to unrelated sibling orgs.
 * Not @Transactional: role switching needs a fresh pooled connection per call, and the interceptor only
 * re-applies SET ROLE when a connection is borrowed from the pool. setupOrganisation derives a distinct db_user
 * per orgSuffix, so several RLS-distinct orgs can coexist in one test.
 */
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class RlsOrgVisibilityIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private IndividualRepository individualRepository;
    @Autowired
    private ConceptRepository conceptRepository;
    @Autowired
    private DataSource dataSource;

    private SubjectType aSubjectType() {
        return testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().build());
    }

    @Test
    public void txRowsAreVisibleOnlyToTheirOwnOrg() {
        TestDataSetupService.TestOrganisationData orgA = testDataSetupService.setupOrganisation("rlsisoa");
        setUser(orgA.getUser());
        TestDataSetupService.TestCatchmentData catchmentA = testDataSetupService.setupACatchment();
        Individual subjectA = testSubjectService.save(new SubjectBuilder()
                .withMandatoryFieldsForNewEntity().withSubjectType(aSubjectType()).withLocation(catchmentA.getAddressLevel1()).build());

        TestDataSetupService.TestOrganisationData orgB = testDataSetupService.setupOrganisation("rlsisob");
        setUser(orgB.getUser());
        TestDataSetupService.TestCatchmentData catchmentB = testDataSetupService.setupACatchment();
        Individual subjectB = testSubjectService.save(new SubjectBuilder()
                .withMandatoryFieldsForNewEntity().withSubjectType(aSubjectType()).withLocation(catchmentB.getAddressLevel1()).build());

        setUser(orgA.getUser());
        assertNotNull("org A must see its own subject", individualRepository.findByUuid(subjectA.getUuid()));
        assertNull("org A must NOT see org B's subject (RLS isolation)", individualRepository.findByUuid(subjectB.getUuid()));

        setUser(orgB.getUser());
        assertNull("org B must NOT see org A's subject (RLS isolation)", individualRepository.findByUuid(subjectA.getUuid()));
        assertNotNull("org B must see its own subject", individualRepository.findByUuid(subjectB.getUuid()));
    }

    @Test
    public void refRowsAreVisibleToDescendantsButNotSiblings() {
        TestDataSetupService.TestOrganisationData parent = testDataSetupService.setupOrganisation("rlsparent");
        setUser(parent.getUser());
        Concept parentConcept = testConceptService.createConcept("rls_ancestor_probe_concept", ConceptDataType.Text);

        TestDataSetupService.TestOrganisationData child = testDataSetupService.setupOrganisation("rlschild");
        TestDataSetupService.TestOrganisationData sibling = testDataSetupService.setupOrganisation("rlssibling");

        // Wire child -> parent as the default super admin (runs as openchs, no SET ROLE -> bypasses org RLS),
        // so the ancestor recursion in org_ids makes the parent's ref data visible to the child.
        setUser(userRepository.getDefaultSuperAdmin());
        new JdbcTemplate(dataSource).update("update public.organisation set parent_organisation_id = ? where id = ?",
                parent.getOrganisationId(), child.getOrganisationId());

        // Query the concept table with raw SQL (no WHERE on organisation_id) so ONLY the RLS policy decides
        // visibility. conceptRepository.findByUuid is overridden to findByUuidAndOrganisationId(uuid, own+ancestors),
        // whose application-level org filter would mask the policy: the sibling assertion would pass even if the
        // ref policy leaked across orgs, because the app filter alone nulls out a foreign-org row. Raw SQL under
        // SET ROLE removes that masking, so this actually exercises V1_398's ref ancestor arm in both directions.
        setUser(child.getUser());
        assertEquals("child org must see its ancestor's concept (ref ancestor arm of the RLS policy)",
                Integer.valueOf(1), countConceptByUuid(parentConcept.getUuid()));

        setUser(sibling.getUser());
        assertEquals("an unrelated sibling org must NOT see the ancestor's concept (RLS isolation)",
                Integer.valueOf(0), countConceptByUuid(parentConcept.getUuid()));
    }

    private Integer countConceptByUuid(String uuid) {
        // public.-qualified: under SET ROLE <org>, an unqualified name resolves to the org's ETL schema.
        return new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from public.concept where uuid = ?", Integer.class, uuid);
    }
}
