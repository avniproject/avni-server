package org.avni.server.service.abha;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.service.IndividualService;
import org.avni.server.service.OrganisationService;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Not @Transactional: each save runs in its own committed transaction, as it does on the server, and the organisation
 * role is applied when a connection is borrowed. tear-down.sql cleans up around each test.
 */
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AbhaLinkServiceIntegrationTest extends AbstractControllerIntegrationTest {
    private static final Long DEMO_ID = 2L;
    // test-data.sql: the default subject type and a location, both OpenCHS's, which demo (its child) uses.
    private static final String SUBJECT_TYPE = "9f2af1f9-e150-4f8e-aad3-40bb7eb05aa3";
    private static final String LOCATION = "ae35fe6d-910e-47bd-a0c7-0c10182a4085";
    private static final String ABHA_NUMBER = "12-3456-7856-8912";
    private static final String ABHA_DIGITS = "12345678568912";
    private static final String ABHA_ADDRESS = "ramesh.gond@sbx";

    @Autowired
    private IndividualService individualService;
    @Autowired
    private AbhaLinkService abhaLinkService;
    @Autowired
    private OrganisationService organisationService;
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private SubjectTypeRepository subjectTypeRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DataSource dataSource;

    private Concept abhaNumber;
    private Concept abhaAddress;

    @Before
    public void createAbhaQuestions() {
        setUser("demo-admin");
        abhaNumber = testConceptService.createConcept("ABHA Number", ConceptDataType.Text);
        abhaAddress = testConceptService.createConcept("ABHA Address", ConceptDataType.Text);
        setUser("demo-user");
    }

    @Test
    public void saveWithAnswerCreatesLink() {
        enableAbha();

        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        assertEquals(Collections.singletonList(live(subject, ABHA_DIGITS, ABHA_ADDRESS)), links());
    }

    // OpenCHS is demo's parent. A parent that adds the questions after its child defined them must not break the child's
    // saves: the organisation's own question is the one its subjects answer.
    @Test
    public void parentDefiningTheQuestionsTooLeavesTheOrganisationsOwnInUse() {
        enableAbha();
        String parentsNumber = parentDefinesConcept("ABHA Number");
        parentDefinesConcept("ABHA Address");
        setUser("demo-user");
        ObservationCollection observations = new ObservationCollection();
        observations.put(abhaNumber.getUuid(), ABHA_NUMBER);
        observations.put(abhaAddress.getUuid(), ABHA_ADDRESS);
        observations.put(parentsNumber, "91-1111-2222-3333");

        Individual subject = save(subjectWith(observations));

        assertEquals(Collections.singletonList(live(subject, ABHA_DIGITS, ABHA_ADDRESS)), links());
    }

    // A question only an ancestor defines is still found.
    @Test
    public void questionDefinedOnlyByTheParentIsUsed() {
        enableAbha();
        setUser(userRepository.getDefaultSuperAdmin());
        new JdbcTemplate(dataSource).update("update public.concept set name = name || ' (demo, retired)' where uuid in (?, ?)", abhaNumber.getUuid(), abhaAddress.getUuid());
        String parentsNumber = parentDefinesConcept("ABHA Number");
        setUser("demo-user");
        ObservationCollection observations = new ObservationCollection();
        observations.put(parentsNumber, ABHA_NUMBER);

        Individual subject = save(subjectWith(observations));

        assertEquals(Collections.singletonList(live(subject, ABHA_DIGITS, null)), links());
    }

    // A text question of OpenCHS's, written as the server's openchs role, which row-level security does not filter.
    private String parentDefinesConcept(String name) {
        setUser(userRepository.getDefaultSuperAdmin());
        String uuid = UUID.randomUUID().toString();
        new JdbcTemplate(dataSource).update("insert into public.concept (name, data_type, uuid, version, organisation_id, created_by_id, " +
                "last_modified_by_id, created_date_time, last_modified_date_time) values (?, 'Text', ?, 1, 1, 1, 1, now(), now())", name, uuid);
        return uuid;
    }

    @Test
    public void abhaDisabledCreatesNothing() {
        save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        assertEquals(Collections.emptyList(), links());
    }

    // Updated in place: the subject keeps one row, which now holds the new number.
    @Test
    public void changedAnswerUpdatesLink() {
        enableAbha();
        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        subject.getObservations().put(abhaNumber.getUuid(), "91-1111-2222-3333");
        subject.getObservations().put(abhaAddress.getUuid(), "ramesh.g@abdm");
        save(subject);

        assertEquals(Collections.singletonList(live(subject, "91111122223333", "ramesh.g@abdm")), links());
    }

    @Test
    public void removedAnswerVoidsLink() {
        enableAbha();
        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        subject.getObservations().remove(abhaNumber.getUuid());
        subject.getObservations().remove(abhaAddress.getUuid());
        save(subject);

        assertEquals(Collections.singletonList(voided(subject, ABHA_DIGITS, ABHA_ADDRESS)), links());
    }

    // Story 13: the server records the removal, naming the user whose save carried it.
    @Test
    public void removedRowOnAnswerRemoval() {
        enableAbha();
        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        subject.getObservations().remove(abhaNumber.getUuid());
        subject.getObservations().remove(abhaAddress.getUuid());
        save(subject);

        assertEquals(Collections.singletonList("removed by demo-user"), removedEvents(subject));
    }

    @Test
    public void removedRowOnSubjectVoid() {
        enableAbha();
        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        new TransactionTemplate(transactionManager).execute(status -> individualService.voidSubject(subject));

        assertEquals(Collections.singletonList("removed by demo-user"), removedEvents(subject));
    }

    @Test
    public void noRemovedRowWhenThereWasNoLink() {
        enableAbha();
        Individual subject = save(subjectWith(null, null));

        new TransactionTemplate(transactionManager).execute(status -> individualService.voidSubject(subject));

        assertEquals(Collections.emptyList(), removedEvents(subject));
    }

    private List<String> removedEvents(Individual subject) {
        return new JdbcTemplate(dataSource).queryForList(
                "select e.action || ' by ' || u.username from public.abha_event e join public.users u on u.id = e.user_id " +
                        "where e.subject_uuid = ? and e.action = 'removed' order by e.id", String.class, subject.getUuid());
    }

    // DELETE /web/subject/{uuid} and the external API void through IndividualService.voidSubject.
    @Test
    public void voidedSubjectVoidsLink() {
        enableAbha();
        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        new TransactionTemplate(transactionManager).execute(status -> individualService.voidSubject(subject));

        assertEquals(Collections.singletonList(voided(subject, ABHA_DIGITS, ABHA_ADDRESS)), links());
    }

    // The app voids a subject by pushing it with voided set, through save.
    @Test
    public void subjectVoidedBySyncVoidsLink() {
        enableAbha();
        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        subject.setVoided(true);
        save(subject);

        assertEquals(Collections.singletonList(voided(subject, ABHA_DIGITS, ABHA_ADDRESS)), links());
    }

    @Test
    public void restoredSubjectRelinks() {
        enableAbha();
        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));
        subject.setVoided(true);
        save(subject);

        subject.setVoided(false);
        save(subject);

        assertEquals(Arrays.asList(voided(subject, ABHA_DIGITS, ABHA_ADDRESS), live(subject, ABHA_DIGITS, ABHA_ADDRESS)), links());
    }

    // An ABHA made with a mobile number only has an address and no number. It is linked too, so a second patient with the
    // same address can be found.
    @Test
    public void anAbhaWithOnlyAnAddressIsLinkedWithoutANumber() {
        enableAbha();
        Individual subject = save(subjectWith(null, ABHA_ADDRESS));

        assertEquals(Collections.singletonList(live(subject, null, ABHA_ADDRESS)), links());
    }

    @Test
    public void removingTheNumberKeepsTheAddressLinked() {
        enableAbha();
        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        subject.getObservations().remove(abhaNumber.getUuid());
        save(subject);

        assertEquals(Collections.singletonList(live(subject, null, ABHA_ADDRESS)), links());
    }

    // The holders of an ABHA address, whatever its case and whether or not it carries its @ suffix.
    @Test
    public void aHolderIsFoundByAddressAlone() {
        enableAbha();
        Individual holder = save(subjectWith(null, "Ramesh.Gond@sbx"));
        SubjectType subjectType = subjectTypeRepository.findByUuid(SUBJECT_TYPE);

        assertEquals(Collections.singletonList(holder.getUuid()), abhaLinkService.findSubjectUUIDs(subjectType, null, "ramesh.gond@sbx"));
        assertEquals(Collections.singletonList(holder.getUuid()), abhaLinkService.findSubjectUUIDs(subjectType, null, "RAMESH.GOND"));
        assertEquals(Collections.emptyList(), abhaLinkService.findSubjectUUIDs(subjectType, null, "ramesh.gond2@sbx"));
    }

    @Test
    public void otherLiveHoldersNamesTheSubjectAlreadyHoldingTheAddressWhenThereIsNoNumber() {
        enableAbha();
        Individual holder = save(subjectWith(null, ABHA_ADDRESS));

        assertEquals(Collections.singletonList(holder.getUuid()), abhaLinkService.otherLiveHolders(subjectWith(null, "Ramesh.Gond@SBX")).subjectUuids());
        assertEquals(AbhaLinkService.ABHA_ADDRESS_CONCEPT, abhaLinkService.otherLiveHolders(subjectWith(null, "Ramesh.Gond@SBX")).answer());
    }

    // A placeholder an upload or a form carries where there is no ABHA (NA, -, 0) is no ABHA address: it is never linked
    // or matched, so rows carrying it are not taken for one patient. A number beside it still links.
    @Test
    public void aPlaceholderInTheAddressIsNoAbha() {
        enableAbha();
        save(subjectWith(null, "NA"));
        save(subjectWith(null, "NA"));
        assertEquals(Collections.emptyList(), links());
        assertEquals(Collections.emptyList(), abhaLinkService.otherLiveHolders(subjectWith(null, "NA")).subjectUuids());

        Individual withNumber = save(subjectWith(ABHA_NUMBER, "-"));
        assertEquals(Collections.singletonList(live(withNumber, ABHA_DIGITS, null)), links());
    }

    // An ABHA number is 14 digits. Anything else is no ABHA number: the address still links when there is one, no link
    // otherwise, and the save goes through, since sync is never refused. The warning names the subject, not the answer.
    @Test
    public void answerNotFourteenDigitsLinksOnlyTheAddress() {
        enableAbha();
        ListAppender<ILoggingEvent> log = captureLog();
        Individual tooLong;
        Individual tooShort;
        try {
            tooLong = save(subjectWith("12-3456-7856-8912-77", ABHA_ADDRESS));
            tooShort = save(subjectWith("12-3456-7856-891", null));
        } finally {
            stopCapturing(log);
        }

        assertEquals(Collections.singletonList(live(tooLong, null, ABHA_ADDRESS)), links());
        assertEquals(2, countSubjects(tooLong, tooShort));
        assertTrue(warnings(log).toString(), warnings(log).stream().anyMatch(w -> w.contains(tooLong.getUuid())));
        assertTrue(warnings(log).toString(), warnings(log).stream().anyMatch(w -> w.contains(tooShort.getUuid())));
        assertNothingLoggedCarries(log, "12345678568912", "1234567856891");
    }

    // The link keeps an address of at most 255 characters. A longer answer would fail the save, and every retry of it:
    // the number is linked without the address, and the warning names the subject, not the answer.
    @Test
    public void addressLongerThan255LinksTheNumberWithoutIt() {
        enableAbha();
        String longAddress = "x".repeat(252) + "@sbx";
        ListAppender<ILoggingEvent> log = captureLog();
        Individual subject;
        try {
            subject = save(subjectWith(ABHA_NUMBER, longAddress));
        } finally {
            stopCapturing(log);
        }

        assertEquals(Collections.singletonList(live(subject, ABHA_DIGITS, null)), links());
        assertTrue(warnings(log).toString(), warnings(log).stream().anyMatch(w -> w.contains(subject.getUuid())));
        assertNothingLoggedCarries(log, longAddress, ABHA_DIGITS, ABHA_NUMBER);
    }

    // A duplicate saved from another system or a second phone is never refused (a bulk upload asks otherLiveHolders
    // first). Both stay linked, and the warning names both subjects, never the number.
    @Test
    public void twoSubjectsSameNumberBothLiveAndWarned() {
        enableAbha();
        Individual first = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));
        ListAppender<ILoggingEvent> log = captureLog();
        Individual second;
        try {
            second = save(subjectWith(ABHA_DIGITS, null));
        } finally {
            stopCapturing(log);
        }

        assertEquals(Arrays.asList(live(first, ABHA_DIGITS, ABHA_ADDRESS), live(second, ABHA_DIGITS, null)), links());
        assertTrue(warnings(log).toString(), warnings(log).stream().anyMatch(w -> w.contains(first.getUuid()) && w.contains(second.getUuid())));
        assertNothingLoggedCarries(log, ABHA_DIGITS, ABHA_NUMBER, ABHA_ADDRESS);
    }

    // What a bulk upload asks before it saves a row: who else already holds this number. The row is refused if anyone
    // does (SubjectWriter). Sync never asks.
    @Test
    public void otherLiveHoldersNamesTheSubjectAlreadyHoldingTheNumber() {
        enableAbha();
        Individual holder = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        assertEquals(Collections.singletonList(holder.getUuid()), abhaLinkService.otherLiveHolders(subjectWith(ABHA_DIGITS, null)).subjectUuids());
        assertEquals(AbhaLinkService.ABHA_NUMBER_CONCEPT, abhaLinkService.otherLiveHolders(subjectWith(ABHA_DIGITS, null)).answer());
    }

    @Test
    public void otherLiveHoldersLeavesOutTheSubjectItself() {
        enableAbha();
        Individual holder = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        assertEquals(Collections.emptyList(), abhaLinkService.otherLiveHolders(holder).subjectUuids());
    }

    @Test
    public void otherLiveHoldersLeavesOutAVoidedHolder() {
        enableAbha();
        Individual holder = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));
        new TransactionTemplate(transactionManager).execute(status -> individualService.voidSubject(holder));

        assertEquals(Collections.emptyList(), abhaLinkService.otherLiveHolders(subjectWith(ABHA_DIGITS, null)).subjectUuids());
    }

    @Test
    // With no ABHA number the address is asked (an ABHA made with a mobile number only); with neither, nobody is.
    public void otherLiveHoldersIsEmptyWithAbhaOffOrNoAbha() {
        save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));
        assertEquals(Collections.emptyList(), abhaLinkService.otherLiveHolders(subjectWith(ABHA_DIGITS, null)).subjectUuids());

        enableAbha();
        Individual holder = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));
        assertEquals(Collections.singletonList(holder.getUuid()), abhaLinkService.otherLiveHolders(subjectWith(null, ABHA_ADDRESS)).subjectUuids());
        assertEquals(Collections.emptyList(), abhaLinkService.otherLiveHolders(subjectWith("1234", null)).subjectUuids());
        assertEquals(Collections.emptyList(), abhaLinkService.otherLiveHolders(subjectWith(null, null)).subjectUuids());
    }

    @Test
    public void hyphenatedAndPlainNumbersMatch() {
        enableAbha();
        Individual hyphenated = save(subjectWith("12-3456-7856-8912", null));
        Individual spaced = save(subjectWith("91 1111 2222 3333", null));
        Individual plain = save(subjectWith("55555666667777", null));
        SubjectType subjectType = subjectTypeRepository.findByUuid(SUBJECT_TYPE);

        assertEquals(Collections.singletonList(hyphenated.getUuid()), abhaLinkService.findSubjectUUIDs(subjectType, "12345678568912"));
        assertEquals(Collections.singletonList(hyphenated.getUuid()), abhaLinkService.findSubjectUUIDs(subjectType, "12 3456 7856 8912"));
        assertEquals(Collections.singletonList(spaced.getUuid()), abhaLinkService.findSubjectUUIDs(subjectType, "91-1111-2222-3333"));
        assertEquals(Collections.singletonList(plain.getUuid()), abhaLinkService.findSubjectUUIDs(subjectType, "55-5556-6666-7777"));
    }

    // Delete data, for a trial organisation, deletes its subjects. Their links go first, or the delete stops on them.
    // An organisation with a parent has its rows deleted through the repositories.
    @Test
    public void deletingTransactionalDataDeletesLinks() {
        assertDeleteDataRemovesLinkAndSubject("abhadeletechild", true);
    }

    // An organisation with no parent has its rows deleted table by table.
    @Test
    public void deletingTransactionalDataOfAnOrganisationWithoutParentDeletesLinks() {
        assertDeleteDataRemovesLinkAndSubject("abhadelete", false);
    }

    // A fresh organisation, since test-data.sql's demo subjects are tied to other organisations' enrolments.
    private void assertDeleteDataRemovesLinkAndSubject(String organisationSuffix, boolean withParent) {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation(organisationSuffix);
        long organisationId = organisation.getOrganisationId();
        setUser(userRepository.getDefaultSuperAdmin());
        String subjectUUID = UUID.randomUUID().toString();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        if (withParent) {
            jdbc.update("update public.organisation set parent_organisation_id = 1 where id = ?", organisationId);
        }
        jdbc.update("insert into public.individual (uuid, address_id, version, date_of_birth, date_of_birth_verified, first_name, last_name, " +
                        "gender_id, organisation_id, subject_type_id, observations, created_by_id, last_modified_by_id, created_date_time, " +
                        "last_modified_date_time) values (?, 1, 1, current_date, false, 'ABHA', 'Holder', 1, ?, " +
                        "(select id from public.subject_type where uuid = ?), '{}'::jsonb, 1, 1, now(), now())",
                subjectUUID, organisationId, SUBJECT_TYPE);
        jdbc.update("insert into public.abha_link (uuid, organisation_id, individual_id, abha_number, created_by_id, last_modified_by_id, " +
                        "created_date_time, last_modified_date_time) values (?, ?, (select id from public.individual where uuid = ?), ?, 1, 1, now(), now())",
                UUID.randomUUID().toString(), organisationId, subjectUUID, ABHA_DIGITS);
        setUser(organisation.getUser());

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                organisationService.deleteTransactionalData(organisationRepository.findOne(organisationId)));

        setUser(userRepository.getDefaultSuperAdmin());
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from public.abha_link where organisation_id = ?", Integer.class, organisationId));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("select count(*) from public.individual where uuid = ?", Integer.class, subjectUUID));
    }

    // The app's route: a registration pushed by sync.
    @Test
    public void syncPushWithAnswerCreatesLink() {
        enableAbha();
        String uuid = "6b0d3ea2-90a4-4c39-8a5e-0d9cbb2c1f01";
        Map<String, Object> request = new HashMap<>();
        request.put("uuid", uuid);
        request.put("firstName", "Ramesh");
        request.put("lastName", "Gond");
        request.put("dateOfBirth", "1997-08-21");
        request.put("gender", "Male");
        request.put("addressLevelUUID", LOCATION);
        request.put("registrationDate", "2018-03-06");
        request.put("observations", Arrays.asList(observation(abhaNumber, ABHA_NUMBER), observation(abhaAddress, ABHA_ADDRESS)));

        post("/individuals", request);

        assertEquals(Collections.singletonList(uuid + " " + ABHA_DIGITS + " " + ABHA_ADDRESS + " org " + DEMO_ID + " live"), links());
    }

    private static Map<String, Object> observation(Concept concept, Object value) {
        Map<String, Object> observation = new HashMap<>();
        observation.put("conceptUUID", concept.getUuid());
        observation.put("value", value);
        return observation;
    }

    private Individual subjectWith(String number, String address) {
        ObservationCollection observations = new ObservationCollection();
        if (number != null) observations.put(abhaNumber.getUuid(), number);
        if (address != null) observations.put(abhaAddress.getUuid(), address);
        return subjectWith(observations);
    }

    private Individual subjectWith(ObservationCollection observations) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(SUBJECT_TYPE);
        AddressLevel location = locationRepository.findByUuid(LOCATION);
        return new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(location)
                .withObservations(observations).build();
    }

    private Individual save(Individual subject) {
        return new TransactionTemplate(transactionManager).execute(status -> individualService.save(subject));
    }

    private static String live(Individual subject, String number, String address) {
        return subject.getUuid() + " " + number + " " + address + " org " + DEMO_ID + " live";
    }

    private static String voided(Individual subject, String number, String address) {
        return subject.getUuid() + " " + number + " " + address + " org " + DEMO_ID + " voided";
    }

    // Every row, live or voided, as "<subject uuid> <number> <address> org <id> live|voided", in creation order.
    private List<String> links() {
        return new JdbcTemplate(dataSource).query(
                "select i.uuid, l.abha_number, l.abha_address, l.organisation_id, l.is_voided from public.abha_link l " +
                        "join public.individual i on i.id = l.individual_id order by l.id",
                (rs, rowNum) -> rs.getString(1) + " " + rs.getString(2) + " " + rs.getString(3) + " org " + rs.getLong(4)
                        + (rs.getBoolean(5) ? " voided" : " live"));
    }

    private int countSubjects(Individual... subjects) {
        int count = 0;
        for (Individual subject : subjects) {
            count += new JdbcTemplate(dataSource).queryForObject("select count(*) from public.individual where uuid = ?", Integer.class, subject.getUuid());
        }
        return count;
    }

    private static ListAppender<ILoggingEvent> captureLog() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(AbhaLinkService.class)).addAppender(appender);
        return appender;
    }

    private static void stopCapturing(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(AbhaLinkService.class)).detachAppender(appender);
    }

    private static List<String> warnings(ListAppender<ILoggingEvent> appender) {
        List<String> warnings = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            if (event.getLevel() == Level.WARN) warnings.add(event.getFormattedMessage());
        }
        return warnings;
    }

    private static void assertNothingLoggedCarries(ListAppender<ILoggingEvent> appender, String... values) {
        for (ILoggingEvent event : appender.list) {
            for (String value : values) {
                assertFalse("logged " + value + ": " + event.getFormattedMessage(), event.getFormattedMessage().contains(value));
            }
        }
    }

    private void enableAbha() {
        new JdbcTemplate(dataSource).update("update organisation_config set settings = settings || '{\"enableAbha\": true}'::jsonb where organisation_id = ?", DEMO_ID);
    }
}
