package org.avni.server.dao;

import org.avni.server.domain.*;
import org.avni.server.domain.individualRelationship.IndividualRelationship;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Repository
public class LinkedParentUuidRepository {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public LinkedParentUuidRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void linkEncounters(Collection<Encounter> encounters) {
        link(encounters, List.of(
                new LinkedParent<>(ParentTable.INDIVIDUAL, Encounter::getIndividual, Encounter::setLinkedIndividualUuid)));
    }

    public void linkProgramEnrolments(Collection<ProgramEnrolment> programEnrolments) {
        link(programEnrolments, List.of(
                new LinkedParent<>(ParentTable.INDIVIDUAL, ProgramEnrolment::getIndividual, ProgramEnrolment::setLinkedIndividualUuid)));
    }

    public void linkProgramEncounters(Collection<ProgramEncounter> programEncounters) {
        link(programEncounters, List.of(
                new LinkedParent<>(ParentTable.PROGRAM_ENROLMENT, ProgramEncounter::getProgramEnrolment, ProgramEncounter::setLinkedProgramEnrolmentUuid)));
    }

    public void linkChecklists(Collection<Checklist> checklists) {
        link(checklists, List.of(
                new LinkedParent<>(ParentTable.PROGRAM_ENROLMENT, Checklist::getProgramEnrolment, Checklist::setLinkedProgramEnrolmentUuid)));
    }

    public void linkChecklistItems(Collection<ChecklistItem> checklistItems) {
        link(checklistItems, List.of(
                new LinkedParent<>(ParentTable.CHECKLIST, ChecklistItem::getChecklist, ChecklistItem::setLinkedChecklistUuid)));
    }

    public void linkSubjectMigrations(Collection<SubjectMigration> subjectMigrations) {
        link(subjectMigrations, List.of(
                new LinkedParent<>(ParentTable.INDIVIDUAL, SubjectMigration::getIndividual, SubjectMigration::setLinkedIndividualUuid)));
    }

    public void linkGroupSubjects(Collection<GroupSubject> groupSubjects) {
        link(groupSubjects, List.of(
                new LinkedParent<>(ParentTable.INDIVIDUAL, GroupSubject::getGroupSubject, GroupSubject::setLinkedGroupSubjectUuid),
                new LinkedParent<>(ParentTable.INDIVIDUAL, GroupSubject::getMemberSubject, GroupSubject::setLinkedMemberSubjectUuid)));
    }

    public void linkIndividualRelationships(Collection<IndividualRelationship> individualRelationships) {
        link(individualRelationships, List.of(
                new LinkedParent<>(ParentTable.INDIVIDUAL, IndividualRelationship::getIndividuala, IndividualRelationship::setLinkedIndividualAUuid),
                new LinkedParent<>(ParentTable.INDIVIDUAL, IndividualRelationship::getIndividualB, IndividualRelationship::setLinkedIndividualBUuid)));
    }

    private <T> void link(Collection<T> entities, List<LinkedParent<T>> linkedParents) {
        Map<ParentTable, Set<Long>> parentIdsByTable = new EnumMap<>(ParentTable.class);
        for (LinkedParent<T> linkedParent : linkedParents) {
            Set<Long> parentIds = parentIdsByTable.computeIfAbsent(linkedParent.table(), table -> new HashSet<>());
            entities.forEach(entity -> parentIds.add(idOf(linkedParent.parent().apply(entity))));
        }

        Map<ParentTable, Map<Long, String>> uuidsByTable = new EnumMap<>(ParentTable.class);
        parentIdsByTable.forEach((table, parentIds) -> uuidsByTable.put(table, findUuids(table, parentIds)));

        for (LinkedParent<T> linkedParent : linkedParents) {
            Map<Long, String> uuids = uuidsByTable.get(linkedParent.table());
            entities.forEach(entity -> linkedParent.linkedUuid().accept(entity, uuids.get(idOf(linkedParent.parent().apply(entity)))));
        }
    }

    private Map<Long, String> findUuids(ParentTable table, Set<Long> ids) {
        Map<Long, String> uuids = new HashMap<>();
        if (ids.isEmpty()) return uuids;
        jdbcTemplate.query("select id, uuid from " + table.getTableName() + " where id = any(?)",
                preparedStatement -> preparedStatement.setArray(1, preparedStatement.getConnection().createArrayOf("bigint", ids.toArray())),
                resultSet -> {
                    uuids.put(resultSet.getLong("id"), resultSet.getString("uuid"));
                });
        return uuids;
    }

    private static Long idOf(CHSBaseEntity entity) {
        LazyInitializer lazyInitializer = HibernateProxy.extractLazyInitializer(entity);
        return lazyInitializer == null ? entity.getId() : (Long) lazyInitializer.getIdentifier();
    }

    private enum ParentTable {
        INDIVIDUAL("individual"),
        PROGRAM_ENROLMENT("program_enrolment"),
        CHECKLIST("checklist");

        private final String tableName;

        ParentTable(String tableName) {
            this.tableName = tableName;
        }

        public String getTableName() {
            return tableName;
        }
    }

    private record LinkedParent<T>(ParentTable table, Function<T, ? extends CHSBaseEntity> parent, BiConsumer<T, String> linkedUuid) {
    }
}
