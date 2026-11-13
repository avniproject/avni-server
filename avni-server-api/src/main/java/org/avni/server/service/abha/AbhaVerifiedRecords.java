package org.avni.server.service.abha;

import com.bugsnag.Bugsnag;
import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.domain.AbhaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// The record verifying the ABHA on a subject's record, for linking and sharing. The phone sends the Eka ID, so an Eka ID
// belongs to at most one live subject of the organisation, the first to claim it; a second claim (a duplicate patient,
// a mistyped ABHA, a forged record) is refused and reported. Logs subject UUIDs only, never an ABHA or an Eka ID.
@Component
public class AbhaVerifiedRecords {
    private static final Logger logger = LoggerFactory.getLogger(AbhaVerifiedRecords.class);

    // Why a subject's visits wait when another live patient claimed its Eka ID first.
    public static final String HELD_BY_ANOTHER = "Eka ID held by another live patient";

    private final AbhaEventRepository abhaEventRepository;
    private final Bugsnag bugsnag;
    // Pairs already sent to Bugsnag: each use of a second claim refuses it, but one clash is one alarm per server.
    private final Set<String> reportedPairs = ConcurrentHashMap.newKeySet();

    // The verified record, if any; heldByAnother when there is none because another live subject claimed its Eka ID first.
    public record Verification(AbhaEvent record, boolean heldByAnother) {
        public static final Verification NONE = new Verification(null, false);

        public Optional<AbhaEvent> verified() {
            return Optional.ofNullable(record);
        }
    }

    @Autowired
    public AbhaVerifiedRecords(AbhaEventRepository abhaEventRepository, Bugsnag bugsnag) {
        this.abhaEventRepository = abhaEventRepository;
        this.bugsnag = bugsnag;
    }

    // Call inside a transaction of the subject's organisation.
    public Optional<AbhaEvent> of(String subjectUuid, String savedNumber, String savedAddress) {
        return check(subjectUuid, savedNumber, savedAddress).verified();
    }

    public Verification check(String subjectUuid, String savedNumber, String savedAddress) {
        Optional<AbhaEventService.Claim> claim = AbhaEventService.claimOfSavedAbha(
                abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc(subjectUuid), savedNumber, savedAddress);
        if (claim.isEmpty()) return Verification.NONE;
        Optional<String> claimedFirstBy = claimedBefore(subjectUuid, claim.get().first());
        if (claimedFirstBy.isPresent()) {
            report(subjectUuid, claimedFirstBy.get());
            return new Verification(null, true);
        }
        return new Verification(claim.get().latest(), false);
    }

    private void report(String subjectUuid, String firstHolder) {
        String clash = "The Eka ID on the ABHA of subject " + subjectUuid + " is claimed first by live subject " + firstHolder
                + "; its records are not linked or shared";
        if (!reportedPairs.add(subjectUuid + " " + firstHolder)) {
            logger.info(clash);
            return;
        }
        IllegalStateException refused = new IllegalStateException(clash);
        logger.warn(clash);
        bugsnag.notify(refused);
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
