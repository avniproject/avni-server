package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmAccess;
import org.avni.server.service.abdm.hiu.AbdmHiuStore;
import org.springframework.stereotype.Component;

// Which Eka webhook events an organisation takes, asked at the webhook and again before the worker handles one. With
// record sharing on, every event. With it off, only a consent update that withdraws, refuses or expires a consent, and
// only while the organisation still holds consents or received records: a patient's withdrawal is honoured and what was
// received under it erased, whatever the switch.
@Component
public class EkaEventGate {
    private final AbdmAccess access;
    private final AbdmHiuStore hiuStore;

    public EkaEventGate(AbdmAccess access, AbdmHiuStore hiuStore) {
        this.access = access;
        this.hiuStore = hiuStore;
    }

    public boolean admits(long organisationId, EkaEventRoute route, JsonNode event) {
        if (access.recordSharingOn(organisationId)) return true;
        return route == EkaEventRoute.CONSENT_UPDATE && EkaHiuService.endsConsent(event) && hiuStore.holdsConsentsOrRecords(organisationId);
    }
}
