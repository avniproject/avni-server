package org.avni.server.dao;

import org.avni.server.domain.GroupRole;
import org.avni.server.domain.SubjectType;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "groupRole", path = "groupRole")
public interface GroupRoleRepository extends ReferenceDataRepository<GroupRole>, FindByLastModifiedDateTime<GroupRole> {

    default GroupRole findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in GroupRole");
    }

    default GroupRole findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in GroupRole");
    }

    GroupRole findByRole(String role);

    GroupRole findByRoleAndGroupSubjectTypeIdAndIsVoidedFalse(String role, Long subjectTypeId);

    List<GroupRole> findByGroupSubjectType_IdAndIsVoidedFalse(Long groupSubjectTypeId);

    List<GroupRole> findByMemberSubjectTypeAndIsVoidedFalse(SubjectType subjectType);
}
