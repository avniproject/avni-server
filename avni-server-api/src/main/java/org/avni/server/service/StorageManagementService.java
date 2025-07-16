package org.avni.server.service;

import jakarta.persistence.EntityManager;
import org.avni.server.dao.DbRoleRepository;
import org.avni.server.domain.StorageManagementConfig;
import org.avni.server.domain.Organisation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Component
public class StorageManagementService {
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private static final List<String> tablesLinkedViaSubjectId = List.of(
            "encounter",
            "program_enrolment",
            "program_encounter",
            "entity_approval_status",
            "subject_migration"
    );
    private static final Logger logger = LoggerFactory.getLogger(StorageManagementService.class);

    @Autowired
    public StorageManagementService(JdbcTemplate jdbcTemplate, EntityManager entityManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Transactional
    public void markSyncDisabled(List<Long> subjectIds, Organisation organisation) {
        assert TransactionSynchronizationManager.isActualTransactionActive();
        DbRoleRepository.setDbRole(entityManager, organisation);
        try {
            updateSubjects(subjectIds);
            updateSubjectLinkedUpdateQuery(subjectIds);
            updateGroupSubject(subjectIds);
            updateIndividualRelationship(subjectIds);
            updateCommentThread(subjectIds);
            updateChecklist(subjectIds);
            updateChecklistItem(subjectIds);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        } finally {
            DbRoleRepository.setDbRoleNone(entityManager);
        }
    }

    public List<Long> getNextSubjectIds(StorageManagementConfig storageManagementConfig) {
        String query = String.format("""
                        SELECT ind.id FROM public.individual ind
                            join (%s) as user_query on user_query.id = ind.id
                            WHERE ind.sync_disabled = false and ind.organisation_id = %d
                            order by ind.id
                         limit 100
                        """,
                storageManagementConfig.getSqlQuery().replace(";", ""), storageManagementConfig.getOrganisationId());
        return jdbcTemplate.query(query, (rs, rowNum) -> rs.getLong("id"));
    }

    private void updateSubjects(List<Long> subjectIds) {
        String query = String.format("""
                            UPDATE public.individual as entity
                                SET sync_disabled = true, sync_disabled_date_time = now()
                            FROM (
                                SELECT id FROM public.individual WHERE id IN (%s)
                            ) subject_ids
                            WHERE entity.id = subject_ids.id
                        """,
                getAsInQueryParam(subjectIds));
        int rowsUpdated = jdbcTemplate.update(query);
        logger.info("Updated {} rows in table: individual using subject ids {}", rowsUpdated, subjectIds);
    }

    private static String getAsInQueryParam(List<Long> subjectIds) {
        return String.join(",", subjectIds.stream().map(String::valueOf).toArray(String[]::new));
    }

    private void updateSubjectLinkedUpdateQuery(List<Long> subjectIds) {
        for (String descendantTable : tablesLinkedViaSubjectId) {
            updateSubjectLinkedTable(subjectIds, descendantTable, "individual_id");
        }
        updateSubjectLinkedTable(subjectIds, "comment", "subject_id");
        updateSubjectLinkedTable(subjectIds, "user_subject_assignment", "subject_id");
        updateSubjectLinkedTable(subjectIds, "subject_program_eligibility", "subject_id");
    }

    private void updateSubjectLinkedTable(List<Long> subjectIds, String descendantTable, String columnName) {
        String query = String.format("""
                    UPDATE public.%s as entity
                        SET sync_disabled = true, sync_disabled_date_time = now()
                    FROM (
                        SELECT id FROM public.individual WHERE id IN (%s)
                    ) subject_ids
                    WHERE entity.%s = subject_ids.id
                """, descendantTable, getAsInQueryParam(subjectIds), columnName);
        int updatedRows = jdbcTemplate.update(query);
        logger.info("Updated {} rows in table: {} with subject ids: {}", updatedRows, descendantTable, subjectIds);
    }

    private void updateCommentThread(List<Long> subjectIds) {
        String query = String.format("""
                    UPDATE public.comment_thread as comment_thread
                        SET sync_disabled = true, sync_disabled_date_time = now()
                    FROM (
                        SELECT id FROM public.individual WHERE id IN (%s)
                    ) subject_ids,
                    public.comment comment
                    WHERE comment.subject_id = subject_ids.id and comment_thread.id = comment.comment_thread_id
                """, getAsInQueryParam(subjectIds));
        int updatedRows = jdbcTemplate.update(query);
        logger.info("Updated {} rows in table: comment_thread for subject ids: {}", updatedRows, subjectIds);
    }

    private void updateChecklistItem(List<Long> subjectIds) {
        String query = String.format("""
                    UPDATE public.checklist_item as checklist_item
                        SET sync_disabled = true, sync_disabled_date_time = now()
                    FROM (
                        SELECT id FROM public.individual WHERE id IN (%s)
                    ) subject_ids,
                    public.checklist checklist,
                    public.program_enrolment enrolment
                    WHERE enrolment.individual_id = subject_ids.id and checklist_item.checklist_id = checklist.id and checklist.program_enrolment_id = enrolment.id
                """, getAsInQueryParam(subjectIds));
        int updatedRows = jdbcTemplate.update(query);
        logger.info("Updated {} rows in table: checklist_item for subject ids: {}", updatedRows, subjectIds);
    }

    private void updateChecklist(List<Long> subjectIds) {
        String query = String.format("""
                    UPDATE public.checklist as checklist
                        SET sync_disabled = true, sync_disabled_date_time = now()
                    FROM (
                        SELECT id FROM public.individual WHERE id IN (%s)
                    ) subject_ids,
                    public.program_enrolment enrolment
                    WHERE enrolment.individual_id = subject_ids.id and checklist.program_enrolment_id = enrolment.id
                """, getAsInQueryParam(subjectIds));
        int updatedRows = jdbcTemplate.update(query);
        logger.info("Updated {} rows in table: checklist for subject ids: {}", updatedRows, subjectIds);
    }

    private void updateIndividualRelationship(List<Long> subjectIds) {
        String query = String.format("""
                    UPDATE public.individual_relationship as individual_relationship
                        SET sync_disabled = true, sync_disabled_date_time = now()
                    FROM (
                        SELECT id FROM public.individual WHERE id IN (%s)
                    ) subject_ids
                    WHERE (individual_relationship.individual_a_id = subject_ids.id or individual_relationship.individual_b_id = subject_ids.id)
                """, getAsInQueryParam(subjectIds));
        int updatedRows = jdbcTemplate.update(query);
        logger.info("Updated {} rows in table: individual_relationship for subject ids: {}", updatedRows, subjectIds);
    }

    private void updateGroupSubject(List<Long> subjectIds) {
        String query = String.format("""
                    UPDATE public.group_subject as group_subject
                        SET sync_disabled = true, sync_disabled_date_time = now()
                    FROM (
                        SELECT id FROM public.individual WHERE id IN (%s)
                    ) subject_ids
                    WHERE (group_subject.group_subject_id = subject_ids.id or group_subject.member_subject_id = subject_ids.id)
                """, getAsInQueryParam(subjectIds));
        int updatedRows = jdbcTemplate.update(query);
        logger.info("Updated {} rows in table: group_subject for subject ids: {}", updatedRows, subjectIds);
    }
}
