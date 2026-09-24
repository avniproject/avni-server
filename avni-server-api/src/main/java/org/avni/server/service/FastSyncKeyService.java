package org.avni.server.service;

import org.avni.server.application.Subject;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.UserGroupRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.avni.server.domain.UserGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.lang.String.format;

@Service
public class FastSyncKeyService {
    private final SubjectTypeRepository subjectTypeRepository;
    private final GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;

    @Autowired
    public FastSyncKeyService(SubjectTypeRepository subjectTypeRepository, GroupRepository groupRepository,
                              UserGroupRepository userGroupRepository) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.groupRepository = groupRepository;
        this.userGroupRepository = userGroupRepository;
    }

    /**
     * Whether this user's sync is narrowed by anything other than their catchment, so that a shared
     * catchment dump would hand them rows they must not have, or hand their catchment peers rows only
     * they may see.
     * <p>
     * The subject-type terms are organisation-level: a User-type or directly-assignable subject type
     * filters each user's sync by their own rows, and a subject type with a usable sync registration
     * concept is filtered by that user's sync attribute values. The axis being present is enough,
     * because a user holding none of those rows today would still share a dump with users who do.
     * <p>
     * The group term is per user, because group privileges differ between users of one organisation
     * and SyncDetailsService gates the syncable items on the privileges of the requesting user. Doing
     * it org-level would both miss the privilege differences it is meant to catch and cost every
     * field worker the shared dump because one colleague is an administrator.
     * <p>
     * Terms are ordered cheapest query first and short-circuit. A wrong true only costs the shared
     * dump; a wrong false is a privilege breach, so anything unrecognised counts as differing.
     */
    public boolean isPerUser(User user) {
        return organisationFiltersRowsPerUser() || userMayDifferFromBaseline(user);
    }

    private boolean organisationFiltersRowsPerUser() {
        return subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User) != null
                || !subjectTypeRepository.findAllByIsVoidedFalseAndIsDirectlyAssignableTrue().isEmpty()
                || hasASubjectTypeWithASyncConcept();
    }

    // Everyone plus SQLite Migration is the privilege baseline every ordinary user shares. A user
    // is only known to hold it when Everyone is actually attached: POST /userGroup/{id} voids any
    // membership by id with no Everyone guard, and User.getUserGroups() filters voided rows out of
    // every repair path, so the detachment is permanent. Such a user holds no privileges at all, so
    // treating "no extra groups" as baseline would hand them a dump full of rows their own sync
    // would never fetch. That also covers an empty membership list, whatever produced it.
    private boolean userMayDifferFromBaseline(User user) {
        Long everyoneGroupId = everyoneGroupId(user);
        List<UserGroup> memberships = userGroupRepository.findByUser_IdAndIsVoidedFalse(user.getId());
        boolean holdsBaseline = memberships.stream()
                .anyMatch(membership -> everyoneGroupId.equals(membership.getGroup().getId()));
        boolean holdsExtras = memberships.stream()
                .anyMatch(membership -> !isBaseline(membership.getGroup(), everyoneGroupId));
        return !holdsBaseline || holdsExtras;
    }

    private boolean isBaseline(Group group, Long everyoneGroupId) {
        return everyoneGroupId.equals(group.getId())
                || Group.SQLITE_MIGRATION_UUID.equals(group.getUuid());
    }

    // By id and uuid, never by name: groups is unique on (uuid, organisation_id) only and a custom
    // group can be renamed to a baseline name, which would hide it from this test. A missing Everyone
    // group throws rather than degrading to the shared key.
    private Long everyoneGroupId(User user) {
        return groupRepository.findByNameAndOrganisationId(Group.Everyone, user.getOrganisationId()).getId();
    }

    private boolean hasASubjectTypeWithASyncConcept() {
        return subjectTypeRepository.findByIsVoidedFalse().stream()
                .anyMatch(SubjectType::isAnySyncRegistrationConceptUsable);
    }

    public String perUserKey(User user) {
        return format("fastsync/%s/fastsync.db", safeSegment(user.getUsername()));
    }

    // Usernames are interpolated into an S3 key. A separator or traversal segment would place the
    // object outside the caller's prefix, which is the whole protection here.
    public static String safeSegment(String username) {
        if (username == null || username.isEmpty()
                || username.contains("/") || username.contains("\\") || username.contains("..")) {
            throw new IllegalArgumentException("Username is not usable as a storage key segment");
        }
        return username;
    }
}
