package org.avni.server.service.abha;

import com.bugsnag.Bugsnag;
import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.domain.AbhaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

// The record verifying the ABHA on a subject's record, for linking and sharing. The phone sends the Eka ID, so an Eka ID
// belongs to at most one live subject of the organisation, the first to claim it; a second claim (a duplicate patient,
// a mistyped ABHA, a forged record) is refused and reported. Logs subject UUIDs only, never an ABHA or an Eka ID.
@Component
public class AbhaVerifiedRecords {
    private static final Logger logger = LoggerFactory.getLogger(AbhaVerifiedRecords.class);

    private final AbhaEventRepository abhaEventRepository;
    private final Bugsnag bugsnag;

    @Autowired
    public AbhaVerifiedRecords(AbhaEventRepository abhaEventRepository, Bugsnag bugsnag) {
        this.abhaEventRepository = abhaEventRepository;
        this.bugsnag = bugsnag;
    }

    // Call inside a transaction of the subject's organisation.
    public Optional<AbhaEvent> of(String subjectUuid, String savedNumber, String savedAddress) {
        Optional<AbhaEventService.Claim> claim = AbhaEventService.claimOfSavedAbha(
                abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc(subjectUuid), savedNumber, savedAddress);
        if (claim.isEmpty()) return Optional.empty();
        Optional<String> claimedFirstBy = claimedBefore(subjectUuid, claim.get().first());
        if (claimedFirstBy.isPresent()) {
            IllegalStateException refused = new IllegalStateException("The Eka ID on the ABHA of subject " + subjectUuid
                    + " is claimed first by live subject " + claimedFirstBy.get() + "; its records are not linked or shared");
            logger.warn(refused.getMessage());
            bugsnag.notify(refused);
            return Optional.empty();
        }
        return Optional.of(claim.get().latest());
    }

    // Another live subject of the organisation whose saved ABHA claimed the same Eka ID with an earlier record.
    private Optional<String> claimedBefore(String subjectUuid, AbhaEvent ours) {
        for (AbhaEventRepository.SavedAbha other : abhaEventRepository.findOtherLiveSubjectsNamingEkaOid(ours.getOrganisationId(), ours.getEkaOid(), subjectUuid)) {
            Optional<AbhaEventService.Claim> theirs = AbhaEventService.claimOfSavedAbha(
                    abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc(other.getSubjectUuid()), other.getAbhaNumber(), other.getAbhaAddress());
            if (theirs.isPresent() && ours.getEkaOid().equals(theirs.get().first().getEkaOid()) && earlier(theirs.get().first(), ours)) {
                return Optional.of(other.getSubjectUuid());
            }
        }
        return Optional.empty();
    }

    // Ids are given in the order the server stored the records.
    private static boolean earlier(AbhaEvent a, AbhaEvent b) {
        return a.getId() != null && (b.getId() == null || a.getId() < b.getId());
    }
}
