package org.avni.server.dao;

import org.avni.server.domain.accessControl.GroupPrivilege;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "groupPrivilege", path = "groupPrivilege")

public interface GroupPrivilegeRepository extends ReferenceDataRepository<GroupPrivilege>, FindByLastModifiedDateTime<GroupPrivilege> {
    default GroupPrivilege findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in GroupPrivilege.");
    }

    default GroupPrivilege findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in GroupPrivilege.");
    }

    List<GroupPrivilege> findByGroup_Id(Long groupId);

    @Query(value = "select distinct gp.*\n" +
            "from group_privilege gp\n" +
            "         join user_group ug on ug.group_id = gp.group_id\n" +
            "         join privilege p on gp.privilege_id = p.id\n" +
            "where ug.user_id = 4\n" +
            "  and gp.is_voided = false\n" +
            "  and ug.is_voided = false\n" +
            "  and allow = true", nativeQuery = true)
    List<GroupPrivilege> getAllAllowedPrivilegesForUser(Long userId);
}
