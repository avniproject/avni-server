package org.avni.server.dao;

import java.time.Instant;
import java.util.Date;
import org.avni.server.domain.User;
import org.avni.server.domain.Group;
import org.avni.server.domain.UserGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
@RepositoryRestResource(collectionResourceRel = "myGroups", path = "myGroups")
public interface UserGroupRepository extends ReferenceDataRepository<UserGroup> {

    Page<UserGroup> findByUserIdAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            Long userId,
            Instant lastModifiedDateTime,
            Instant now,
            Pageable pageable);

    default UserGroup findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in UserGroup.");
    }

    default UserGroup findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in UserGroup.");
    }

    UserGroup findByUserAndGroupAndIsVoidedFalse(User user, Group group);

    List<UserGroup> findByGroup_IdAndIsVoidedFalse(Long groupId);

    List<UserGroup> findByOrganisationId(Long organisationId);

    @RestResource(exported = false)
    Long deleteAllByGroupIsNotIn(List<Group> groups);

    @Modifying
    @Query("DELETE FROM UserGroup ug where ug.group in (:groups)")
    int deleteAllByGroupIn(List<Group> groups);

    boolean existsByUserIdAndLastModifiedDateTimeGreaterThan(Long userId, Instant lastModifiedDateTime);

    List<UserGroup> findByUserAndGroupHasAllPrivilegesTrueAndIsVoidedFalse(User user);

}
