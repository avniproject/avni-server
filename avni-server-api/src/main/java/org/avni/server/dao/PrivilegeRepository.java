package org.avni.server.dao;

import org.avni.server.domain.PrivilegeEntityType;
import org.avni.server.domain.accessControl.Privilege;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.joda.time.DateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "privilege", path = "privilege")
public interface PrivilegeRepository extends AvniJpaRepository<Privilege, Long> {
    @RestResource(path = "lastModified", rel = "lastModified")
    Page<Privilege> findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant now,
            Pageable pageable);

    Privilege findByUuid(String uuid);

    List<Privilege> findAllByIsVoidedFalse();

    boolean existsByLastModifiedDateTimeGreaterThan(Instant lastModifiedDateTime);

    Privilege findByType(PrivilegeType privilegeType);

    List<Privilege> findAllByIsVoidedFalseAndEntityTypeIn(List<PrivilegeEntityType> privilegeEntityType);

    default List<Privilege> getAdminPrivileges() {
        return this.findAllByIsVoidedFalseAndEntityTypeIn(Collections.singletonList(PrivilegeEntityType.NonTransaction));
    }

    boolean existsByEntityTypeAndTypeAndIsVoidedFalse(PrivilegeEntityType privilegeEntityType, PrivilegeType privilegeType);

    boolean existsByEntityTypeAndTypeInAndIsVoidedFalse(PrivilegeEntityType privilegeEntityType, List<PrivilegeType> privilegeTypes);

    default boolean isAllowedForAdmin(PrivilegeType privilegeType) {
        return this.existsByEntityTypeAndTypeAndIsVoidedFalse(PrivilegeEntityType.NonTransaction, privilegeType);
    }

    default boolean isAnyOfSpecificAllowedForAdmin(List<PrivilegeType> privilegeTypes) {
        return this.existsByEntityTypeAndTypeInAndIsVoidedFalse(PrivilegeEntityType.NonTransaction, privilegeTypes);
    }
}
