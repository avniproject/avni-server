package org.avni.server.dao;

import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.joda.time.DateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Repository;

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

    List<GroupPrivilege> findByGroup_Id(Long groupId);

    @Query(value = "select distinct gp.*\n" +
            "from group_privilege gp\n" +
            "         join user_group ug on ug.group_id = gp.group_id\n" +
            "         join privilege p on gp.privilege_id = p.id\n" +
            "where ug.user_id = :userId\n" +
            "  and gp.is_voided = false\n" +
            "  and ug.is_voided = false\n" +
            "  and allow = true", nativeQuery = true)
    List<GroupPrivilege> getAllAllowedPrivilegesForUser(Long userId);

    @RestResource(path = "lastModified", rel = "lastModified")
    Page<GroupPrivilege> findBySubjectTypeIsNotNullAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date now,
            Pageable pageable);

    default Page<GroupPrivilege> findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        return findBySubjectTypeIsNotNullAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), pageable);
    }

    boolean existsByLastModifiedDateTimeGreaterThan(@Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date lastModifiedDateTime);

    default boolean existsByLastModifiedDateTimeGreaterThan(DateTime lastModifiedDateTime) {
        return existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime == null ? null : lastModifiedDateTime.toDate());
    }
}
