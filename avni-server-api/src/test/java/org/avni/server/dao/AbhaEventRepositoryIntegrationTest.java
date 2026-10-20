package org.avni.server.dao;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.builder.TestDataSetupService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class AbhaEventRepositoryIntegrationTest extends AbstractControllerIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-20T06:31:00Z");

    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AbhaEventRepository abhaEventRepository;

    @Before
    public void setUp() {
        testDataSetupService.setupOrganisation();
    }

    @Test
    public void savesAndReReadsEveryColumn() {
        AbhaEvent saved = abhaEventRepository.saveAndFlush(event("subject-1", "created", "oid-1", NOW));

        AbhaEvent reRead = abhaEventRepository.findByUuid(saved.getUuid());
        assertNotNull(reRead.getCreatedBy());
        assertEquals(UserContextHolder.getUserContext().getOrganisationId(), reRead.getOrganisationId());
        assertEquals("subject-1", reRead.getSubjectUuid());
        assertEquals(UserContextHolder.getUserContext().getUser().getId(), reRead.getUserId());
        assertEquals("HIP-1", reRead.getHipId());
        assertEquals("created", reRead.getAction());
        assertEquals("aadhaar", reRead.getRoute());
        assertEquals("12345678568912", reRead.getAbhaNumber());
        assertEquals("a@abdm", reRead.getAbhaAddress());
        assertEquals("oid-1", reRead.getEkaOid());
        assertEquals("txn-1", reRead.getEkaTxnId());
        assertEquals(NOW.minusSeconds(60), reRead.getConsentGivenAt());
        assertEquals(NOW, reRead.getOccurredAt());
    }

    // A failed insert aborts the test's own transaction, so the constraint is read from the catalogue instead.
    @Test
    public void uuidIsUniqueWithinAnOrganisationAndEventsAreIndexedBySubjectAndByUser() {
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint where conrelid = 'public.abha_event'::regclass and contype = 'u' " +
                        "and pg_get_constraintdef(oid) = 'UNIQUE (uuid, organisation_id)'", Integer.class));
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where tablename = 'abha_event' and indexdef like '%(organisation_id, subject_uuid)'", Integer.class));
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where tablename = 'abha_event' and indexdef like '%(organisation_id, user_id, occurred_at)'", Integer.class));
        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
                "select relrowsecurity from pg_class where oid = 'public.abha_event'::regclass", Boolean.class));
    }

    @Test
    public void newestEventWithAnEkaIdForASubject() {
        abhaEventRepository.save(event("subject-1", "created", "oid-old", NOW.minusSeconds(3600)));
        abhaEventRepository.save(event("subject-1", "linked", null, NOW.plusSeconds(60)));
        abhaEventRepository.save(event("subject-1", "linked", "oid-new", NOW));
        abhaEventRepository.save(event("subject-2", "created", "oid-other", NOW.plusSeconds(600)));

        Optional<AbhaEvent> latest = abhaEventRepository.findFirstBySubjectUuidAndEkaOidIsNotNullOrderByOccurredAtDesc("subject-1");

        assertEquals("oid-new", latest.orElseThrow().getEkaOid());
        assertFalse(abhaEventRepository.findFirstBySubjectUuidAndEkaOidIsNotNullOrderByOccurredAtDesc("subject-3").isPresent());
    }

    @Test
    public void whetherThisUserRecordedAnEventSince() {
        Long me = UserContextHolder.getUserContext().getUser().getId();
        abhaEventRepository.save(event("subject-1", "created", "oid-1", NOW));

        assertTrue(abhaEventRepository.existsBySubjectUuidAndUserIdAndOccurredAtAfter("subject-1", me, NOW.minusSeconds(1800)));
        assertFalse("too long ago", abhaEventRepository.existsBySubjectUuidAndUserIdAndOccurredAtAfter("subject-1", me, NOW.plusSeconds(1)));
        assertFalse("another subject", abhaEventRepository.existsBySubjectUuidAndUserIdAndOccurredAtAfter("subject-2", me, NOW.minusSeconds(1800)));
        assertFalse("another user", abhaEventRepository.existsBySubjectUuidAndUserIdAndOccurredAtAfter("subject-1", me + 1000, NOW.minusSeconds(1800)));
    }

    private AbhaEvent event(String subjectUuid, String action, String ekaOid, Instant occurredAt) {
        AbhaEvent event = new AbhaEvent();
        event.setUuid(UUID.randomUUID().toString());
        event.setOrganisationId(UserContextHolder.getUserContext().getOrganisationId());
        event.setSubjectUuid(subjectUuid);
        event.setUserId(UserContextHolder.getUserContext().getUser().getId());
        event.setHipId("HIP-1");
        event.setAction(action);
        event.setRoute("aadhaar");
        event.setAbhaNumber("12345678568912");
        event.setAbhaAddress("a@abdm");
        event.setEkaOid(ekaOid);
        event.setEkaTxnId("txn-1");
        event.setConsentGivenAt(occurredAt.minusSeconds(60));
        event.setOccurredAt(occurredAt);
        return event;
    }
}
