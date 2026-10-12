package org.avni.server.dao.sync;

import jakarta.persistence.EntityManager;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.LocationMappingRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.SyncParameters;
import org.avni.server.dao.attendance.AttendanceRecordRepository;
import org.avni.server.dao.attendance.SessionRepository;
import org.avni.server.dao.calendar.CalendarDateMarkerRepository;
import org.avni.server.dao.calendar.CalendarRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestLocationService;
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class CatchmentChangeCheckIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String BEFORE_LAST_SYNC = "2000-01-01";
    private static final DateTime LAST_SYNC = new DateTime(2010, 1, 1, 0, 0);

    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestLocationService testLocationService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private LocationMappingRepository locationMappingRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired
    private CalendarRepository calendarRepository;
    @Autowired
    private CalendarDateMarkerRepository calendarDateMarkerRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private TestDataSetupService.TestCatchmentData catchmentData;
    private SubjectType groupSubjectType;
    private long organisationId;
    private long userId;
    private AddressLevel locationInCatchment;
    private AddressLevel locationOutsideCatchment;
    private long sessionInCatchment;
    private long sessionOutsideCatchment;
    private long calendarInCatchment;
    private long calendarOutsideCatchment;

    @Before
    public void setup() {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        organisationId = organisationData.getOrganisationId();
        userId = organisationData.getUser().getId();
        catchmentData = testDataSetupService.setupACatchment();
        locationInCatchment = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(catchmentData.getAddressLevelType()).parent(catchmentData.getAddressLevel1()).build());
        locationOutsideCatchment = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(catchmentData.getAddressLevelType()).parent(catchmentData.getAddressLevel2()).build());
        groupSubjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setGroup(true).setUuid("groupSubjectType").setName("groupSubjectType").build());
        Individual groupInCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectType).withLocation(locationInCatchment).build());
        Individual groupOutsideCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(groupSubjectType).withLocation(locationOutsideCatchment).build());
        entityManager.flush();

        insertLocationMapping(locationInCatchment, catchmentData.getAddressLevel1());
        insertLocationMapping(locationOutsideCatchment, catchmentData.getAddressLevel2());
        long attendanceType = insert("insert into attendance_type (uuid, subject_type_id, name, config, organisation_id, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) values (?, ?, 'Daily', '{}', ?, ?, ?, now(), now()) returning id",
                UUID.randomUUID().toString(), groupSubjectType.getId(), organisationId, userId, userId);
        sessionInCatchment = insertSession(groupInCatchment, attendanceType);
        sessionOutsideCatchment = insertSession(groupOutsideCatchment, attendanceType);
        insertAttendanceRecord(sessionInCatchment, groupInCatchment);
        insertAttendanceRecord(sessionOutsideCatchment, groupOutsideCatchment);
        calendarInCatchment = insertCalendar(locationInCatchment);
        calendarOutsideCatchment = insertCalendar(locationOutsideCatchment);
        insertCalendarDateMarker(calendarInCatchment);
        insertCalendarDateMarker(calendarOutsideCatchment);

        for (String table : new String[]{"address_level", "location_location_mapping", "session", "attendance_record", "calendar", "calendar_date_marker"}) {
            jdbcTemplate.update("update " + table + " set last_modified_date_time = ?::timestamptz where organisation_id = ?", BEFORE_LAST_SYNC, organisationId);
        }
    }

    @Test
    public void nothingChangedSinceTheLastSync() {
        assertNoKindChanged();
    }

    @Test
    public void changesOutsideTheCatchmentAreNotReported() {
        touch("address_level", catchmentData.getAddressLevel2().getId());
        touch("address_level", locationOutsideCatchment.getId());
        jdbcTemplate.update("update location_location_mapping set last_modified_date_time = now() where location_id = ?", locationOutsideCatchment.getId());
        touch("session", sessionOutsideCatchment);
        jdbcTemplate.update("update attendance_record set last_modified_date_time = now() where session_id = ?", sessionOutsideCatchment);
        touch("calendar", calendarOutsideCatchment);
        jdbcTemplate.update("update calendar_date_marker set last_modified_date_time = now() where calendar_id = ?", calendarOutsideCatchment);

        assertNoKindChanged();
    }

    @Test
    public void aChangedLocationInTheCatchmentIsReported() {
        touch("address_level", locationInCatchment.getId());
        assertTrue(locationRepository.isEntityChanged(syncParameters()));
    }

    @Test
    public void aChangedLocationMappingInTheCatchmentIsReported() {
        jdbcTemplate.update("update location_location_mapping set last_modified_date_time = now() where location_id = ?", locationInCatchment.getId());
        assertTrue(locationMappingRepository.isEntityChanged(syncParameters()));
    }

    @Test
    public void aChangedSessionInTheCatchmentIsReported() {
        touch("session", sessionInCatchment);
        assertTrue(sessionRepository.isEntityChanged(syncParameters()));
    }

    @Test
    public void aChangedAttendanceRecordInTheCatchmentIsReported() {
        jdbcTemplate.update("update attendance_record set last_modified_date_time = now() where session_id = ?", sessionInCatchment);
        assertTrue(attendanceRecordRepository.isEntityChanged(syncParameters()));
    }

    @Test
    public void aChangedCalendarInTheCatchmentIsReported() {
        touch("calendar", calendarInCatchment);
        assertTrue(calendarRepository.isEntityChanged(syncParameters()));
    }

    @Test
    public void aChangedCalendarDateMarkerInTheCatchmentIsReported() {
        jdbcTemplate.update("update calendar_date_marker set last_modified_date_time = now() where calendar_id = ?", calendarInCatchment);
        assertTrue(calendarDateMarkerRepository.isEntityChanged(syncParameters()));
    }

    private void assertNoKindChanged() {
        SyncParameters syncParameters = syncParameters();
        assertFalse(locationRepository.isEntityChanged(syncParameters));
        assertFalse(locationMappingRepository.isEntityChanged(syncParameters));
        assertFalse(sessionRepository.isEntityChanged(syncParameters));
        assertFalse(attendanceRecordRepository.isEntityChanged(syncParameters));
        assertFalse(calendarRepository.isEntityChanged(syncParameters));
        assertFalse(calendarDateMarkerRepository.isEntityChanged(syncParameters));
    }

    private SyncParameters syncParameters() {
        return new SyncParameters(LAST_SYNC, DateTime.now(), groupSubjectType.getId(), groupSubjectType.getUuid(), null,
                Collections.emptyList(), groupSubjectType, null, null, catchmentData.getCatchment());
    }

    private void touch(String table, long id) {
        jdbcTemplate.update("update " + table + " set last_modified_date_time = now() where id = ?", id);
    }

    private long insert(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private void insertLocationMapping(AddressLevel location, AddressLevel parent) {
        jdbcTemplate.update("insert into location_location_mapping (location_id, parent_location_id, version, uuid, organisation_id, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) values (?, ?, 0, ?, ?, ?, ?, now(), now())",
                location.getId(), parent.getId(), UUID.randomUUID().toString(), organisationId, userId, userId);
    }

    private long insertSession(Individual group, long attendanceType) {
        return insert("insert into session (uuid, group_subject_id, scheduled_date, attendance_type_id, status, organisation_id, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) values (?, ?, current_date, ?, 'Scheduled', ?, ?, ?, now(), now()) returning id",
                UUID.randomUUID().toString(), group.getId(), attendanceType, organisationId, userId, userId);
    }

    private void insertAttendanceRecord(long session, Individual subject) {
        jdbcTemplate.update("insert into attendance_record (uuid, session_id, subject_id, status, organisation_id, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) values (?, ?, ?, 'Present', ?, ?, ?, now(), now())",
                UUID.randomUUID().toString(), session, subject.getId(), organisationId, userId, userId);
    }

    private long insertCalendar(AddressLevel location) {
        return insert("insert into calendar (uuid, name, address_level_id, organisation_id, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) values (?, ?, ?, ?, ?, ?, now(), now()) returning id",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), location.getId(), organisationId, userId, userId);
    }

    private void insertCalendarDateMarker(long calendar) {
        jdbcTemplate.update("insert into calendar_date_marker (uuid, calendar_id, marker_date, organisation_id, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) values (?, ?, current_date, ?, ?, ?, now(), now())",
                UUID.randomUUID().toString(), calendar, organisationId, userId, userId);
    }
}
