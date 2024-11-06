package org.avni.server.dao;

import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.util.DateTimeUtil;
import org.joda.time.DateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "groupPrivilege", path = "groupPrivilege")
public interface GroupPrivilegeRepository extends ReferenceDataRepository<GroupPrivilege> {
    default GroupPrivilege findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in GroupPrivilege.");
    }

    default GroupPrivilege findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in GroupPrivilege.");
    }

    List<GroupPrivilege> findByGroup_IdAndImplVersion(Long groupId, int implVersion);

    @Query(value = "select distinct gp.*\n" +
            "from group_privilege gp\n" +
            "         join user_group ug on ug.group_id = gp.group_id\n" +
            "         join privilege p on gp.privilege_id = p.id\n" +
            "where ug.user_id = :userId\n" +
            "  and gp.is_voided = false\n" +
            "  and gp.impl_version = 1\n" +
            "  and ug.is_voided = false\n" +
            "  and allow = true", nativeQuery = true)
    List<GroupPrivilege> getAllAllowedPrivilegesForUser(Long userId);

    @RestResource(path = "lastModified", rel = "lastModified")
    Page<GroupPrivilege> findBySubjectTypeIsNotNullAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant now,
            Pageable pageable);

    boolean existsByLastModifiedDateTimeGreaterThan(@Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastModifiedDateTime);

    default boolean existsByLastModifiedDateTimeGreaterThan(DateTime lastModifiedDateTime) {
        return existsByLastModifiedDateTimeGreaterThan(DateTimeUtil.toInstant(lastModifiedDateTime));
    }

    default GroupPrivilege saveGroupPrivilege(GroupPrivilege groupPrivilege) {
        groupPrivilege.setImplVersion(GroupPrivilege.IMPL_VERSION);
        return this.save(groupPrivilege);
    }

    default List<GroupPrivilege> saveAllGroupPrivileges(List<GroupPrivilege> groupPrivileges) {
        groupPrivileges.forEach(gp -> gp.setImplVersion(GroupPrivilege.IMPL_VERSION));
        return this.saveAll(groupPrivileges);
    }

    List<GroupPrivilege> findByImplVersion(int implVersion);
}
