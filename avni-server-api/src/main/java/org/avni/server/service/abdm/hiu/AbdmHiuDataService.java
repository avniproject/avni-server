package org.avni.server.service.abdm.hiu;

import org.avni.server.service.abdm.hip.AbdmFlows;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Avni as HIU, the records half: the records received under a consent request, and their expiry and erasure.
// EkaHiuService receives, decrypts and stores them.
@Service
public class AbdmHiuDataService {
    private static final java.util.Set<String> ENDED = java.util.Set.of("REVOKED", "EXPIRED", "DENIED");
    private static final Logger logger = LoggerFactory.getLogger(AbdmHiuDataService.class);

    private final AbdmHiuStore store;
    private final AbdmRecordReads reads;

    public AbdmHiuDataService(AbdmHiuStore store, AbdmRecordReads reads) {
        this.store = store;
        this.reads = reads;
    }

    // Hourly (AbdmHousekeepingJob): consents past their erase date expire, and records no live grant covers are deleted.
    public void expireAndErase() {
        int expired = store.expireOverdueConsents();
        int erased = store.eraseRecordsOfEndedConsents();
        if (expired > 0 || erased > 0) logger.info("ABDM HIU: {} consent request(s) expired, {} record(s) erased", expired, erased);
    }

    // The same for the organisation named, needing no user: its system user cannot act, and erasure must not wait on it.
    public void expireAndErase(long organisationId) {
        int expired = store.expireOverdueConsents(organisationId);
        int erased = store.eraseRecordsOfEndedConsents(organisationId);
        if (expired > 0 || erased > 0) {
            logger.info("ABDM HIU, organisation {} with no login: {} consent request(s) expired, {} record(s) erased", organisationId, expired, erased);
        }
    }

    // [{careContextReference, hipId, transactionId, receivedAt, checksumMatched, bundle}]. Nothing once the consent is
    // withdrawn, refused or expired, or past its erase date, and only records under an artefact still granted, whether or
    // not the hourly erase has run yet (HIU_FLOW_202, HIU_FLOW_301). Each call is logged first (AbdmRecordReads).
    public List<Map<String, Object>> records(String consentRequestUuid) {
        AbdmHiuStore.Consent request = store.findConsentRequestByUuid(consentRequestUuid)
                .orElseThrow(() -> AbdmRequestException.notFound("Consent request not found"));
        reads.logged(consentRequestUuid);
        List<Map<String, Object>> out = new ArrayList<>();
        if (ENDED.contains(request.status())) return out;
        // A request Eka refused never got an id there, so nothing can have arrived under it.
        if (request.consentRequestId() == null) return out;
        if (request.dataEraseAt() != null && !request.dataEraseAt().isAfter(java.time.Instant.now())) return out;
        java.util.Set<String> granted = store.findArtefacts(List.of(request.consentRequestId())).stream()
                .filter(a -> "GRANTED".equals(a.status())).map(AbdmHiuStore.Artefact::consentId).collect(java.util.stream.Collectors.toSet());
        for (AbdmHiuStore.Record r : store.records(request.consentRequestId())) {
            if (!granted.contains(r.consentId())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("careContextReference", r.careContextReference());
            m.put("hipId", r.hipId());
            m.put("transactionId", r.transactionId());
            m.put("receivedAt", AbdmFlows.iso(r.receivedAt()));
            m.put("checksumMatched", r.checksumMatched());
            m.put("bundle", r.bundle());
            out.add(m);
        }
        return out;
    }
}
