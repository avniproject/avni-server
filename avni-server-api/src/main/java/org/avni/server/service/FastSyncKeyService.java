package org.avni.server.service;

import org.avni.server.application.Subject;
import org.avni.server.dao.SubjectTypeRepository;
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
     * Whether this user's sync is narrowed by anything other than their catchment. Three of the
     * four axes in OperatingIndividualScopeAwareRepository.addSyncStrategyPredicates are per-user,
     * and two of those are organisation properties: when a directly-assignable or User-type subject
     * type exists, every user's sync for it is filtered by their own rows, whether or not they hold
     * any today. A shared catchment dump is wrong for the whole organisation in that case.
     */
    public boolean isPerUser(User user) {
        return hasSyncAttributes(user)
                || !subjectTypeRepository.findAllByIsVoidedFalseAndIsDirectlyAssignableTrue().isEmpty()
                || subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User) != null;
    }

    private boolean hasSyncAttributes(User user) {
        if (user.getSyncSettings() == null) return false;
        return user.getSyncSettings().get(User.SyncSettingKeys.syncAttribute1.name()) != null
                || user.getSyncSettings().get(User.SyncSettingKeys.syncAttribute2.name()) != null;
    }

    public String perUserKey(User user) {
        return format("fastsync/%s/fastsync.db", safeSegment(user.getUsername()));
    }

    // Usernames are interpolated into an S3 key. A separator or traversal segment would place the
    // object outside the caller's prefix, which is the whole protection here.
    private String safeSegment(String username) {
        if (username == null || username.isEmpty()
                || username.contains("/") || username.contains("\\") || username.contains("..")) {
            throw new IllegalArgumentException("Username is not usable as a storage key segment");
        }
        return username;
    }
}
