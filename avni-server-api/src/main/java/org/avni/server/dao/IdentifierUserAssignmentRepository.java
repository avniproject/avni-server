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

    @Query(value = "select other_identifier_assignment.* from identifier_user_assignment other_identifier_assignment where other_identifier_assignment.identifier_source_id = :identifierSourceId and replace(:identifierStart, :prefix, '')::int >= replace(other_identifier_assignment.identifier_start, :prefix, '')::int and replace(:identifierStart, :prefix, '')::int <= replace(other_identifier_assignment.identifier_end, :prefix, '')::int and other_identifier_assignment.is_voided = false", nativeQuery = true)
    List<IdentifierUserAssignment> getOverlappingAssignment(String prefix, long identifierSourceId, String identifierStart);

    default List<IdentifierUserAssignment> getOverlappingAssignmentForIdentifierSourceWithPrefix(IdentifierUserAssignment identifierUserAssignment) {
        return getOverlappingAssignment(identifierUserAssignment.getIdentifierSource().getPrefix(), identifierUserAssignment.getIdentifierSource().getId(), identifierUserAssignment.getIdentifierStart());
    }
}
