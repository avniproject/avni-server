package org.avni.server.service;

import org.avni.server.application.Subject;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static java.lang.String.format;

@Service
public class FastSyncKeyService {
    private final SubjectTypeRepository subjectTypeRepository;

    @Autowired
    public FastSyncKeyService(SubjectTypeRepository subjectTypeRepository) {
        this.subjectTypeRepository = subjectTypeRepository;
    }

    /**
     * Whether this user's sync is narrowed by something a restoring device cannot undo, so that a
     * shared catchment dump would leave them holding rows they must not have.
     * <p>
     * Both remaining terms are organisation-level: the axis being present is enough, because a user
     * holding none of those rows today would still share a dump with users who do.
     * <p>
     * A User-type subject type holds one subject per field worker, joined to that worker's own
     * user_subject row and never scoped by location, so a catchment dump carries every peer's. The
     * device cannot drop them: the client asks for its syncable items with includeUserSubjectType=true,
     * so the type is in the restoring user's own allowlist and its rows survive privilege reconciliation.
     * A subject type with a usable sync registration concept is filtered by each user's syncAttribute
     * values, which is a per-row distinction, and reconciliation on the device works by entity type.
     * Tracked as avniproject/avni-client#2153.
     * <p>
     * Two narrower terms used to sit here and no longer do, because the device now reconciles a
     * restored dump against its own user (PeerOwnedData.js, shared by the Realm and SQLite restores).
     * Directly assignable subject types went because clearDirectlyAssignedSubjects deletes the
     * uploader's caseload and resets the affected checkpoints, so the next sync re-pulls only what
     * this user is assigned. Group privilege differences went because clearEntitiesOutsidePrivileges
     * fetches the restoring user's own syncable items and removes every subject type they do not name.
     * <p>
     * Terms are ordered cheapest query first and short-circuit. A wrong true only costs the shared
     * dump; a wrong false is a privilege breach, so anything unrecognised counts as differing.
     */
    public boolean isPerUser(User user) {
        return subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User) != null
                || hasASubjectTypeWithASyncConcept();
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
