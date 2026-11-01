package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.OperatingIndividualScope;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestGroupService;
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.EntityModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * avni-client#2099: rows sharing one last_modified_date_time across a page boundary.
 * The phone's watermark is the last row's timestamp; afterUuid is the last row's uuid, so a resumed sync
 * continues inside the group instead of starting it again.
 */
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class SyncCursorIntegrationTest extends AbstractControllerIntegrationTest {
    private static final int PAGE_SIZE = 2;

    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private IndividualController individualController;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SubjectType subjectType;
    private final DateTime groupTime = new DateTime(2024, 1, 15, 10, 30, 0, 123);
    private final DateTime laterTime = groupTime.plusMinutes(5);
    private List<String> groupInSyncOrder;
    private String laterRow;

    @Before
    public void setup() {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData catchmentData = testDataSetupService.setupACatchment();
        subjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("cursorSubjectType").setName("cursorSubjectType").build());
        testGroupService.giveViewSubjectPrivilegeTo(organisationData.getGroup(), subjectType);
        userRepository.save(new UserBuilder(organisationData.getUser()).withCatchment(catchmentData.getCatchment()).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).build());

        List<Individual> group = new ArrayList<>();
        for (int i = 0; i < 5; i++)
            group.add(testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).build()));
        Individual later = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).build());

        jdbcTemplate.update("update individual set last_modified_date_time = ? where subject_type_id = ?", new Timestamp(groupTime.getMillis()), subjectType.getId());
        jdbcTemplate.update("update individual set last_modified_date_time = ? where id = ?", new Timestamp(laterTime.getMillis()), later.getId());
        groupInSyncOrder = jdbcTemplate.queryForList("select uuid from individual where subject_type_id = ? and last_modified_date_time = ? order by id", String.class, subjectType.getId(), new Timestamp(groupTime.getMillis()));
        laterRow = later.getUuid();
        assertEquals(5, groupInSyncOrder.size());

        setUser(organisationData.getUser().getUsername());
    }

    private List<String> pull(DateTime lastModifiedDateTime, String afterUuid, int page) {
        return individualController.getIndividualsByOperatingIndividualScopeAsSlice(lastModifiedDateTime, DateTime.now(), subjectType.getUuid(), afterUuid, PageRequest.of(page, PAGE_SIZE))
                .getContent().stream().map(EntityModel::getContent).map(Individual::getUuid).collect(Collectors.toList());
    }

    @Test
    @Transactional
    public void withoutCursorAResumeInsideTheGroupStartsTheGroupAgain() {
        List<String> firstPage = pull(groupTime.minusDays(1), null, 0);
        assertEquals(groupInSyncOrder.subList(0, 2), firstPage);

        // Interrupted here. The phone stored loadedSince = groupTime and resumes from page 0.
        List<String> resumed = pull(groupTime, null, 0);
        assertEquals("today's behaviour: the rows already received come back again", firstPage, resumed);
    }

    @Test
    @Transactional
    public void cursorResumeContinuesInsideTheGroupAndDeliversEveryRowOnce() {
        List<String> received = new ArrayList<>(pull(groupTime.minusDays(1), null, 0));

        // Interrupted. Resume with (loadedSince, lastUuid) of the last row received, walking pages as the phone does today.
        String lastUuid = received.get(received.size() - 1);
        for (int page = 0; page < 10; page++) {
            List<String> rows = pull(groupTime, lastUuid, page);
            received.addAll(rows);
            if (rows.size() < PAGE_SIZE) break;
        }

        List<String> expected = new ArrayList<>(groupInSyncOrder);
        expected.add(laterRow);
        assertEquals(expected, received);
    }

    @Test
    @Transactional
    public void cursorMovedAfterEveryPageDeliversEveryRowOnce() {
        List<String> received = new ArrayList<>();
        DateTime loadedSince = groupTime.minusDays(1);
        String lastUuid = null;
        for (int request = 0; request < 10; request++) {
            List<String> rows = pull(loadedSince, lastUuid, 0);
            if (rows.isEmpty()) break;
            received.addAll(rows);
            lastUuid = rows.get(rows.size() - 1);
            loadedSince = lastUuid.equals(laterRow) ? laterTime : groupTime;
        }

        List<String> expected = new ArrayList<>(groupInSyncOrder);
        expected.add(laterRow);
        assertEquals(expected, received);
        assertEquals(received.size(), new HashSet<>(received).size());
    }

    @Test
    @Transactional
    public void cursorAtTheLastRowOfTheGroupReturnsOnlyLaterRows() {
        assertEquals(List.of(laterRow), pull(groupTime, groupInSyncOrder.get(4), 0));
    }

    @Test
    @Transactional
    public void unknownCursorUuidFallsBackToTheWholeGroupRatherThanSkippingIt() {
        assertEquals(groupInSyncOrder.subList(0, 2), pull(groupTime, "no-such-uuid", 0));
    }
}
