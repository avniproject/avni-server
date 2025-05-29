package org.avni.server.service;

import org.avni.server.domain.ArchivalConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class StorageManagementService {
    private final JdbcTemplate jdbcTemplate;
    private static final List<String> tablesLinkedViaSubjectId = List.of(
            "encounter",
            "program_enrollment",
            "program_encounter",
            "entity_approval_status",
            "checklist",
            "individual_relationship",
            "subject_migration"
    );
    private static final Logger logger = LoggerFactory.getLogger(StorageManagementService.class);

    @Autowired
    public StorageManagementService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSyncDisabled(List<Long> subjectIds) {
        updateSubjects(subjectIds);
        updateSubjectLinkedUpdateQuery(subjectIds);
        updateCommentThread(subjectIds);
        updateChecklistItem(subjectIds);
    }

    public List<Long> getNextSubjectIds(ArchivalConfig archivalConfig) {
        String query = String.format("%s limit 100", archivalConfig.getSqlQuery());
        return jdbcTemplate.query(query, (rs, rowNum) -> rs.getLong("id"));
    }

    private void updateSubjects(List<Long> subjectIds) {
        String query = String.format("""
                            UPDATE public.individual entity SET entity.sync_disabled = true
                            FROM (
                                SELECT id FROM public.individual WHERE id IN (%s)
                            ) subject_ids
                            WHERE entity.id = subject_ids.id
                        """,
                getAsInQueryParam(subjectIds));
        int rowsUpdated = jdbcTemplate.update(query);
        logger.info("Updated {} rows in table: individual", rowsUpdated);
    }

    private static String getAsInQueryParam(List<Long> subjectIds) {
        return String.join(",", subjectIds.stream().map(String::valueOf).toArray(String[]::new));
    }

    private void updateSubjectLinkedUpdateQuery(List<Long> subjectIds) {
        for (String descendantTable : tablesLinkedViaSubjectId) {
            updateSubjectLinkedTable(subjectIds, descendantTable, "individual_id");
        }
        updateSubjectLinkedTable(subjectIds, "comment", "subject_id");
    }

    private void updateSubjectLinkedTable(List<Long> subjectIds, String descendantTable, String columnName) {
        String query = String.format("""
                    UPDATE public.%s entity SET entity.sync_disabled = true
                    FROM (
                        SELECT id FROM public.individual WHERE id IN (%s)
                    ) subject_ids
                    WHERE entity.%s = subject_ids.id
                """, descendantTable, getAsInQueryParam(subjectIds), columnName);
        int updatedRows = jdbcTemplate.update(query);
        logger.info("Updated {} rows in table: {}}", updatedRows, descendantTable);
    }

    private void updateCommentThread(List<Long> subjectIds) {
        String query = String.format("""
                    UPDATE public.comment_thread entity SET entity.sync_disabled = true
                    FROM (
                        SELECT id FROM public.individual WHERE id IN (%s)
                    ) subject_ids,
                    public.comment comment
                    WHERE comment.subject_id = subject_ids.id and entity.comment_id = comment.id
                """, getAsInQueryParam(subjectIds));
        int updatedRows = jdbcTemplate.update(query);
        logger.info("Updated {} rows in table: comment_thread}", updatedRows);
    }

    private void updateChecklistItem(List<Long> subjectIds) {
        String query = String.format("""
                    UPDATE public.checklist_item entity SET entity.sync_disabled = true
                    FROM (
                        SELECT id FROM public.individual WHERE id IN (%s)
                    ) subject_ids,
                    public.program_enrolment enrolment
                    WHERE enrolment.individual_id = subject_ids.id and entity.checklist_id = enrolment.id
                """, getAsInQueryParam(subjectIds));
        int updatedRows = jdbcTemplate.update(query);
        logger.info("Updated {} rows in table: checklist_item}", updatedRows);
    }
}
