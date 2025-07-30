package org.avni.server.dao;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.QueryHint;
import org.avni.server.domain.Catchment;
import org.avni.server.domain.User;
import org.avni.server.projection.UserWebProjection;
import org.avni.server.web.request.api.RequestUtils;
import org.joda.time.DateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static org.avni.server.domain.User.DEFAULT_SUPER_ADMIN;

@Repository
@RepositoryRestResource(collectionResourceRel = "user", path = "user")
public interface UserRepository extends AvniJpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    @QueryHints({@QueryHint(name = org.hibernate.jpa.QueryHints.HINT_CACHEABLE, value = "true")})
    User findByUsername(String username);

    @QueryHints({@QueryHint(name = org.hibernate.jpa.QueryHints.HINT_CACHEABLE, value = "true")})
    User findByUsernameIgnoreCaseAndIsVoidedFalse(String username);

    @QueryHints({@QueryHint(name = org.hibernate.jpa.QueryHints.HINT_CACHEABLE, value = "true")})
    User findByUuid(String uuid);

    Optional<User> findById(Long id);

    default User findOne(Long id) {
        return findById(id).orElse(null);
    }

    Page<User> findByOrganisationIdAndIsVoidedFalse(@Param("organisationId") Long organisationId,
                                                    Pageable pageable);

    @RestResource(exported = false)
    List<User> findByIdIn(@Param("ids") Long[] ids);

    List<UserWebProjection> findAllByOrganisationIdAndIsVoidedFalse(Long organisationId);
    List<User> findAllByIsVoidedFalseAndOrganisationId(Long organisationId);
    List<User> findAllByOrganisationId(Long organisationId);

    @Query(value = "SELECT u FROM User u left join u.accountAdmin as aa " +
            "where u.isVoided = false " +
            "and (aa.account.id in (:accountIds)) " +
            "and (:username is null or u.username like %:username%) " +
            "and (:name is null or u.name like %:name%) " +
            "and (:email is null or u.email like %:email%) " +
            "and (:phoneNumber is null or u.phoneNumber like %:phoneNumber%)")
    Page<User> findAccountAndOrgAdmins(String username, String name, String email, String phoneNumber, List<Long> accountIds, Pageable pageable);

    @Query(value = "SELECT u FROM User u left join u.accountAdmin as aa " +
            "where u.id=:id and u.isVoided = false and " +
            "(((:organisationIds) is not null and u.organisationId in (:organisationIds) and u.isOrgAdmin = true) or aa.account.id in (:accountIds))")
    User getOne(Long id, List<Long> accountIds, List<Long> organisationIds);

    boolean existsByLastModifiedDateTimeGreaterThan(DateTime lastModifiedDateTime);

    List<User> findByCatchment_IdInAndIsVoidedFalse(List<Long> catchmentIds);

    List<User> findByCatchmentAndIsVoidedFalse(Catchment catchment);

    default User getUser(String userId) throws EntityNotFoundException {
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

    Optional<User> findByPhoneNumber(String phoneNumber);

    String BASE_PRIVILEGE_QUERY = "select (count(p.id) > 0) as exists from group_privilege\n" +
            "    join privilege p on group_privilege.privilege_id = p.id\n" +
            "    join groups on group_privilege.group_id = groups.id and groups.is_voided = false\n" +
            "    join user_group ug on groups.id = ug.group_id and ug.is_voided = false\n" +
            "    join users on ug.user_id = users.id\n" +
            "where users.id = :userId and group_privilege.allow and group_privilege.is_voided = false";
    String PRIVILEGE_TYPE_CLAUSE = " and p.type = :type";
    String PRIVILEGE_TYPES_CLAUSE = " and p.type in :types";
    @Query(value = BASE_PRIVILEGE_QUERY + PRIVILEGE_TYPE_CLAUSE, nativeQuery = true)
    boolean hasPrivilege(String type, long userId);

    @Query(value = BASE_PRIVILEGE_QUERY + PRIVILEGE_TYPES_CLAUSE, nativeQuery = true)
    boolean hasAnyOfSpecificPrivileges(List<String> types, long userId);

    @Query(value = "select bool_or(groups.has_all_privileges) from users\n" +
            "    left outer join user_group ug on users.id = ug.user_id and ug.is_voided = false\n" +
            "    left outer join groups on ug.group_id = groups.id and groups.is_voided = false\n" +
            "where users.id = :userId", nativeQuery = true)
    Boolean hasAll(long userId);

    String BASE_ENTITY_TYPE_QUERY = "select (count(p.id) > 0) as exists from group_privilege\n" +
            "    join privilege p on group_privilege.privilege_id = p.id\n" +
            "    join groups on group_privilege.group_id = groups.id and groups.is_voided=false\n" +
            "    join user_group ug on groups.id = ug.group_id and ug.is_voided=false\n" +
            "    join users on ug.user_id = users.id\n" +
            "where users.id = :userId and group_privilege.allow and group_privilege.is_voided = false";

    String BASE_ENTITY_TYPE_PRIVILEGE_QUERY = BASE_ENTITY_TYPE_QUERY + PRIVILEGE_TYPE_CLAUSE;
    String BASE_ENTITY_TYPE_PRIVILEGE_LIST_QUERY = BASE_ENTITY_TYPE_QUERY + PRIVILEGE_TYPES_CLAUSE;

    @Query(value = BASE_ENTITY_TYPE_PRIVILEGE_QUERY + " and group_privilege.subject_type_id = :subjectTypeId", nativeQuery = true)
    boolean hasSubjectPrivilege(String type, long subjectTypeId, long userId);

    @Query(value = BASE_ENTITY_TYPE_PRIVILEGE_LIST_QUERY + " and group_privilege.subject_type_id = :subjectTypeId", nativeQuery = true)
    boolean hasAnyOfSpecificSubjectPrivileges(List<String> types, long subjectTypeId, long userId);

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

    default User getDefaultSuperAdmin() {
        return this.findByUuid(DEFAULT_SUPER_ADMIN);
    }

    @Query(value = "select * from users where lower(users.settings->>'idPrefix') = lower(:prefix) and id <> :exceptUserId", nativeQuery = true)
    List<User> getUsersWithSameIdPrefix(String prefix, long exceptUserId);

    @Query(value = "select * from users where lower(users.settings->>'idPrefix') = lower(:prefix)", nativeQuery = true)
    List<User> getAllUsersWithSameIdPrefix(String prefix);

    User findTopByOrderByIdDesc();

    List<User> findAllByEmailIgnoreCaseAndIsVoidedFalse(String email);

    default User getLatestUser() {
        return this.findTopByOrderByIdDesc();
    }
}
