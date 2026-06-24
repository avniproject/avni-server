package org.avni.server.rls;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Regression for #1009: the Hibernate second-level (L2) entity cache must not leak a row across orgs.
 *
 * The leak: the entity cache is keyed by {@code (entityClass, id)}, so a by-id lookup that another org
 * already loaded into the cache returned that org's row without ever reaching the DB (where RLS would
 * have blocked it). {@link org.avni.server.framework.hibernate.AvniTenantIdentifierResolver} folds the
 * org into the cache key, so org B's by-id lookup misses org A's entry, hits the DB, and RLS returns
 * nothing.
 *
 * Concept is L2-cached (see ehcache.xml) and is ref data, so two unrelated orgs cannot see each other's
 * concepts. Without the tenant resolver registered this test fails: org B's findOne cache-hits org A's
 * entry and returns org A's concept. Not {@code @Transactional} — RLS role switching needs a fresh
 * pooled connection per call (the JDBC interceptor re-applies SET ROLE only when a connection is borrowed).
 */
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class L2CacheOrgIsolationIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private ConceptRepository conceptRepository;

    @Test
    public void l2EntityCacheMustNotServeAnotherOrgsRowById() {
        TestDataSetupService.TestOrganisationData orgA = testDataSetupService.setupOrganisation("l2isoa");
        setUser(orgA.getUser());
        Concept concept = testConceptService.createConcept("l2_cache_probe_concept", ConceptDataType.Text);
        Long conceptId = conceptRepository.findByUuid(concept.getUuid()).getId();
        // Load by id as org A to populate the L2 entity cache (keyed, after the fix, by org A's tenant id).
        assertNotNull("org A must see its own concept by id", conceptRepository.findOne(conceptId));

        TestDataSetupService.TestOrganisationData orgB = testDataSetupService.setupOrganisation("l2isob");
        setUser(orgB.getUser());
        // Before the fix this id is a shared L2 cache key and returns org A's concept (the leak).
        assertNull("org B must NOT see org A's concept via the L2 entity cache (id-keyed leak)",
                conceptRepository.findOne(conceptId));
    }
}
