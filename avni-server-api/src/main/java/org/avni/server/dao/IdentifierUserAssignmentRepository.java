package org.avni.server.dao;

import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.IdentifierUserAssignment;
import org.avni.server.domain.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "identifierUserAssignment", path = "identifierUserAssignment")
public interface IdentifierUserAssignmentRepository extends ReferenceDataRepository<IdentifierUserAssignment>, FindByLastModifiedDateTime<IdentifierUserAssignment> {

    @Query("select iua " +
            "from IdentifierUserAssignment iua " +
            "where iua.assignedTo = :user and iua.identifierSource = :identifierSource and " +
            "      (iua.lastAssignedIdentifier is null or " +
            "      iua.identifierEnd <> iua.lastAssignedIdentifier)" +
            "    order by iua.identifierStart asc")
    List<IdentifierUserAssignment> getAllNonExhaustedUserAssignments(
            @Param("user") User user,
            @Param("identifierSource") IdentifierSource identifierSource);

    default IdentifierUserAssignment findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in IdentifierUserAssignment");
    }

    default IdentifierUserAssignment findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in IdentifierUserAssignment");
    }

    @Query(value = "select other_identifier_assignment.* from identifier_user_assignment other_identifier_assignment where other_identifier_assignment.identifier_source_id = :identifierSourceId and ((cast(replace(:identifierStart, :prefix, '') as integer) between cast(replace(other_identifier_assignment.identifier_start, :prefix, '') as integer) and cast(replace(other_identifier_assignment.identifier_end, :prefix, '') as integer)) OR (cast(replace(:identifierEnd, :prefix, '') as integer) between cast(replace(other_identifier_assignment.identifier_start, :prefix, '') as integer) and cast(replace(other_identifier_assignment.identifier_end, :prefix, '') as integer))) and other_identifier_assignment.is_voided = false", nativeQuery = true)
    List<IdentifierUserAssignment> getOverlappingAssignment(String prefix, long identifierSourceId, String identifierStart, String identifierEnd);

    @Query(value = "select other_identifier_assignment.* from identifier_user_assignment other_identifier_assignment where other_identifier_assignment.identifier_source_id = :identifierSourceId and other_identifier_assignment.assigned_to_user_id = :userId and ((cast(replace(:identifierStart, :prefix, '') as integer) between cast(replace(other_identifier_assignment.identifier_start, :prefix, '') as integer) and cast(replace(other_identifier_assignment.identifier_end, :prefix, '') as integer)) OR (cast(replace(:identifierEnd, :prefix, '') as integer) between cast(replace(other_identifier_assignment.identifier_start, :prefix, '') as integer) and cast(replace(other_identifier_assignment.identifier_end, :prefix, '') as integer))) and other_identifier_assignment.is_voided = false", nativeQuery = true)
    List<IdentifierUserAssignment> getOverlappingAssignmentWithSamePrefix(String prefix, long identifierSourceId, String identifierStart, String identifierEnd, long userId);

    default List<IdentifierUserAssignment> getOverlappingAssignmentForPooledIdentifier(IdentifierUserAssignment identifierUserAssignment) {
        return getOverlappingAssignment(identifierUserAssignment.getIdentifierSource().getPrefix(), identifierUserAssignment.getIdentifierSource().getId(), identifierUserAssignment.getIdentifierStart(), identifierUserAssignment.getIdentifierEnd());
    }

    default List<IdentifierUserAssignment> getOverlappingAssignmentForNonPooledIdentifier(IdentifierUserAssignment identifierUserAssignment) {
        User assignedTo = identifierUserAssignment.getAssignedTo();
        return getOverlappingAssignmentWithSamePrefix(assignedTo.getUserSettings().getIdPrefix(), identifierUserAssignment.getIdentifierSource().getId(), identifierUserAssignment.getIdentifierStart(), identifierUserAssignment.getIdentifierEnd(), assignedTo.getId());
    }
}
