package org.avni.server.service;

import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class OrganisationServiceDeleteRegistryTest {

    @Test
    public void txJpaTableListContainsAttendanceEntitiesInFkSafeOrder() throws Exception {
        List<String> list = invokeTableList("getTxJpaTableList");
        assertTrue("attendance_record must be in the transactional table list", list.contains("attendance_record"));
        assertTrue("session must be in the transactional table list", list.contains("session"));
        assertTrue("attendance_record must be deleted before session (FK attendance_record.session_id -> session.id)",
                list.indexOf("attendance_record") < list.indexOf("session"));
        assertTrue("attendance_record must be deleted before individual (FK on subject_id)",
                list.indexOf("attendance_record") < list.indexOf("individual"));
        assertTrue("session must be deleted before individual (FK on group_subject_id)",
                list.indexOf("session") < list.indexOf("individual"));
    }

    @Test
    public void metadataJpaTableListContainsCalendarAndAttendanceTypeInFkSafeOrder() throws Exception {
        List<String> list = invokeTableList("getMetadataJpaTableList");
        assertTrue("calendar_date_marker must be in the metadata table list", list.contains("calendar_date_marker"));
        assertTrue("calendar must be in the metadata table list", list.contains("calendar"));
        assertTrue("attendance_type must be in the metadata table list", list.contains("attendance_type"));
        assertTrue("calendar_date_marker must be deleted before calendar (FK calendar_date_marker.calendar_id -> calendar.id)",
                list.indexOf("calendar_date_marker") < list.indexOf("calendar"));
        assertTrue("attendance_type must be deleted before subject_type (FK attendance_type.subject_type_id -> subject_type.id)",
                list.indexOf("attendance_type") < list.indexOf("subject_type"));
    }

    @Test
    public void metadataJpaTableListContainsCustomCardConfigInFkSafeOrder() throws Exception {
        List<String> list = invokeTableList("getMetadataJpaTableList");
        assertTrue("custom_card_config must be in the metadata table list", list.contains("custom_card_config"));
        assertTrue("report_card must be deleted before custom_card_config (FK report_card.custom_card_config_id -> custom_card_config.id)",
                list.indexOf("report_card") < list.indexOf("custom_card_config"));
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeTableList(String methodName) throws Exception {
        OrganisationService svc = Mockito.mock(OrganisationService.class, Mockito.CALLS_REAL_METHODS);
        Method m = OrganisationService.class.getDeclaredMethod(methodName);
        m.setAccessible(true);
        return (List<String>) m.invoke(svc);
    }
}
