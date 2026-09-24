package org.avni.server.service;

import org.avni.server.application.Subject;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static java.lang.String.format;

@Service
public class FastSyncKeyService {
    private final SubjectTypeRepository subjectTypeRepository;
    private final GroupRepository groupRepository;

    @Autowired
    public FastSyncKeyService(SubjectTypeRepository subjectTypeRepository, GroupRepository groupRepository) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.groupRepository = groupRepository;
    }

    /**
     * Whether this organisation narrows a sync by anything other than the catchment, so that two users
     * in one catchment can legitimately hold different data and a shared catchment dump would hand
     * one of them rows they must not have.
     * <p>
     * Every term is an organisation property rather than a property of any one user, because the axis
     * being present is enough: a User-type or directly-assignable subject type filters each user's
     * sync by their own rows, a subject type with a usable sync registration concept is filtered by
     * that user's sync attribute values, and a non-default group means SyncDetailsService gates the
     * syncable items on that user's group privileges. A user holding none of them today would still
     * share a dump with users who do, so the answer is the same for every user in the organisation.
     * Terms are ordered cheapest query first and short-circuit.
     */
    public boolean isPerUserOrganisation() {
        return subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User) != null
                || !subjectTypeRepository.findAllByIsVoidedFalseAndIsDirectlyAssignableTrue().isEmpty()
                || hasANonDefaultGroup()
                || hasASubjectTypeWithASyncConcept();
    }

    private boolean hasANonDefaultGroup() {
        return groupRepository.findByIsVoidedFalse().stream()
                .anyMatch(group -> !group.isOneOfTheDefaultGroups());
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
