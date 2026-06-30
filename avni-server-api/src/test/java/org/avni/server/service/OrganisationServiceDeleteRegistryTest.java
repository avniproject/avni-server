package org.avni.server.service;

import org.avni.server.dao.DownloadableContentRepository;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
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

    @Test
    public void metadataJpaTableListContainsDownloadableContentForOrgDeleteAndReset() throws Exception {
        List<String> list = invokeTableList("getMetadataJpaTableList");
        assertTrue("downloadable_content must be in the metadata table list (org delete/reset coverage)",
                list.contains("downloadable_content"));
    }

    @Test
    public void metadataJpaTableListContainsModelKeyForOrgDeleteAndReset() throws Exception {
        List<String> list = invokeTableList("getMetadataJpaTableList");
        assertTrue("model_key must be in the metadata table list (org delete/reset coverage)",
                list.contains("model_key"));
    }

    @Test
    public void metadataJpaTableListContainsOrgStorageCredentialForOrgDeleteAndReset() throws Exception {
        List<String> list = invokeTableList("getMetadataJpaTableList");
        assertTrue("org_storage_credential must be in the metadata table list (org delete/reset coverage)",
                list.contains("org_storage_credential"));
    }

    @Test
    public void metadataJpaRepositoriesContainsDownloadableContentRepository() throws Exception {
        DownloadableContentRepository downloadableContentRepository = Mockito.mock(DownloadableContentRepository.class);
        OrganisationService svc = Mockito.mock(OrganisationService.class, Mockito.CALLS_REAL_METHODS);
        Field field = OrganisationService.class.getDeclaredField("downloadableContentRepository");
        field.setAccessible(true);
        field.set(svc, downloadableContentRepository);

        Method m = OrganisationService.class.getDeclaredMethod("getMetadataJpaRepositories");
        m.setAccessible(true);
        JpaRepository[] repositories = (JpaRepository[]) m.invoke(svc);

        assertTrue("DownloadableContentRepository must be in getMetadataJpaRepositories() (org delete/reset coverage)",
                Arrays.asList(repositories).contains(downloadableContentRepository));
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeTableList(String methodName) throws Exception {
        OrganisationService svc = Mockito.mock(OrganisationService.class, Mockito.CALLS_REAL_METHODS);
        Method m = OrganisationService.class.getDeclaredMethod(methodName);
        m.setAccessible(true);
        return (List<String>) m.invoke(svc);
    }
}
