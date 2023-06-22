package org.avni.server.dao;

import org.avni.server.domain.Catchment;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.projection.UserWebProjection;
import org.avni.server.web.request.api.RequestUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Repository;

import org.joda.time.DateTime;

import javax.persistence.EntityNotFoundException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;

@Repository
@RepositoryRestResource(collectionResourceRel = "user", path = "user")
public interface UserRepository extends PagingAndSortingRepository<User, Long>, JpaSpecificationExecutor<User> {

    User findByUsername(String username);

    User findByUuid(String uuid);

    Optional<User> findById(Long id);

    @PreAuthorize(value = "hasAnyAuthority('user', 'organisation_admin', 'admin')")
    default User findOne(Long id) {
        return findById(id).orElse(null);
    }

    @PreAuthorize("hasAnyAuthority('admin', 'user')")
    User save(User user);

    @PreAuthorize("hasAnyAuthority('admin','organisation_admin')")
    @RestResource(path = "findByOrganisation", rel = "findByOrganisation")
    Page<User> findByOrganisationIdAndIsVoidedFalse(@Param("organisationId") Long organisationId,
                                                    Pageable pageable);

    @PreAuthorize("hasAnyAuthority('admin','organisation_admin')")
    @RestResource(path = "findAllById", rel = "findAllById")
    List<User> findByIdIn(@Param("ids") Long[] ids);

    List<UserWebProjection> findAllByOrganisationIdAndIsVoidedFalse(Long organisationId);

    @Query(value = "SELECT u FROM User u left join u.accountAdmin as aa " +
            "where u.isVoided = false and " +
            "(((:organisationIds) is not null and u.organisationId in (:organisationIds) and u.isOrgAdmin = true) or aa.account.id in (:accountIds)) " +
            "and (:username is null or u.username like %:username%) " +
            "and (:name is null or u.name like %:name%) " +
            "and (:email is null or u.email like %:email%) " +
            "and (:phoneNumber is null or u.phoneNumber like %:phoneNumber%)")
    Page<User> findAccountAndOrgAdmins(String username, String name, String email, String phoneNumber, List<Long> accountIds, List<Long> organisationIds, Pageable pageable);

    @Query(value = "SELECT u FROM User u left join u.accountAdmin as aa " +
            "where u.id=:id and u.isVoided = false and " +
            "(((:organisationIds) is not null and u.organisationId in (:organisationIds) and u.isOrgAdmin = true) or aa.account.id in (:accountIds))")
    User getOne(Long id, List<Long> accountIds, List<Long> organisationIds);

    @PreAuthorize("hasAnyAuthority('user')")
    boolean existsByLastModifiedDateTimeGreaterThan(DateTime lastModifiedDateTime);

    List<User> findByCatchment_IdInAndIsVoidedFalse(List<Long> catchmentIds);

    List<User> findByCatchmentAndIsVoidedFalse(Catchment catchment);

    default Optional<User> findUserWithMatchingPropertyValue(String propertyName, String value) {
        Specification<User> specification = (Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.like(root.get(propertyName), "%" + value + "%");

        List<User> users = findAll(specification);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    default User getUser(String userId) {
        User user = null;
        if (RequestUtils.isValidUUID(userId)) {
            user = findByUuid(userId);
        } else {
            user = findOne(Long.parseLong(userId));
        }
        if (user == null) {
            throw new EntityNotFoundException("User not found with id / uuid: " + userId);
        }
        return user;
    }

    @Query(value = "select (count(p.id) > 0) as exists from group_privilege\n" +
            "    join privilege p on group_privilege.privilege_id = p.id\n" +
            "    join groups on group_privilege.group_id = groups.id\n" +
            "    join user_group ug on groups.id = ug.group_id\n" +
            "    join users on ug.user_id = users.id\n" +
            "where p.type = :type and users.id = :userId and group_privilege.allow", nativeQuery = true)
    boolean hasPrivilege(String type, long userId);

    @Query(value = "select bool_or(groups.has_all_privileges) from users\n" +
            "    left outer join user_group ug on users.id = ug.user_id\n" +
            "    left outer join groups on ug.group_id = groups.id\n" +
            "where users.id = :userId", nativeQuery = true)
    Boolean hasAll(long userId);

    static final String BASE_ENTITY_TYPE_PRIVILEGE_QUERY = "select (count(p.id) > 0) as exists from group_privilege\n" +
            "    join privilege p on group_privilege.privilege_id = p.id\n" +
            "    join groups on group_privilege.group_id = groups.id\n" +
            "    join user_group ug on groups.id = ug.group_id\n" +
            "    join users on ug.user_id = users.id\n" +
            "where p.type = :type and users.id = :userId and group_privilege.allow";

    @Query(value = BASE_ENTITY_TYPE_PRIVILEGE_QUERY + " and group_privilege.subject_type_id = :subjectTypeId", nativeQuery = true)
    boolean hasSubjectPrivilege(String type, long subjectTypeId, long userId);

    @Query(value = BASE_ENTITY_TYPE_PRIVILEGE_QUERY + " and group_privilege.program_id = :programId", nativeQuery = true)
    boolean hasProgramPrivilege(String type, long programId, long userId);

    @Query(value = BASE_ENTITY_TYPE_PRIVILEGE_QUERY + " and group_privilege.program_encounter_type_id = :encounterTypeId", nativeQuery = true)
    boolean hasProgramEncounterPrivilege(String type, long encounterTypeId, long userId);

    @Query(value = BASE_ENTITY_TYPE_PRIVILEGE_QUERY + " and group_privilege.encounter_type_id = :encounterTypeId", nativeQuery = true)
    boolean hasEncounterPrivilege(String type, long encounterTypeId, long userId);

    default boolean hasAllPrivileges(long userId) {
        Boolean aBoolean = this.hasAll(userId);
        return aBoolean != null && aBoolean;
    }
}
