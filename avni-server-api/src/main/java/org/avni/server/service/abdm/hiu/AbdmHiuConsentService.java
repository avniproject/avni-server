package org.avni.server.service.abdm.hiu;

import org.avni.server.service.abdm.hip.AbdmFlows;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// DEMO-ONLY abdm-hackathon: never merged.
// Avni as HIU: the consent requests as the admin page lists them. EkaHiuService asks for consent through Eka and records
// the answers.
@Service
public class AbdmHiuConsentService {
    private final AbdmHiuStore store;

    public AbdmHiuConsentService(AbdmHiuStore store) {
        this.store = store;
    }

    // [{uuid, abhaAddress, patientName, abhaNumber (masked), consentRequestId, purposeCode, hiTypes, from, to, expiry,
    //   status, statusHistory:[{status, at}], error, createdAt, artefacts:[{consentId, status, dataFlowStatus, records}]}]
    // NHA's list (HIU_FLOW_104): the patient, the ABHA number, the record types, the dates, the expiry, and when each
    // status came. The dates and expiry are what the patient granted once granted (EkaHiuService), else what was asked.
    public List<Map<String, Object>> list() {
        List<AbdmHiuStore.Consent> requests = store.listConsentRequests();
        List<String> requestIds = requests.stream().map(AbdmHiuStore.Consent::consentRequestId).filter(id -> id != null).collect(Collectors.toList());
        Map<String, List<AbdmHiuStore.Artefact>> artefacts = store.findArtefacts(requestIds)
                .stream().collect(Collectors.groupingBy(AbdmHiuStore.Artefact::consentRequestId));
        Map<String, AbdmHiuStore.Patient> patients = store.patientsByAbhaAddress(requests.stream().map(AbdmHiuStore.Consent::abhaAddress)
                .filter(a -> a != null).distinct().collect(Collectors.toList()));
        Map<String, List<Map<String, Object>>> history = new LinkedHashMap<>();
        for (AbdmHiuStore.StatusChange c : store.statusHistory(requestIds)) {
            if (c.consentId() != null) continue;
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("status", c.status());
            h.put("at", AbdmFlows.iso(c.at()));
            history.computeIfAbsent(c.consentRequestId(), k -> new ArrayList<>()).add(h);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (AbdmHiuStore.Consent r : requests) {
            Map<String, Object> m = new LinkedHashMap<>();
            AbdmHiuStore.Patient patient = r.abhaAddress() == null || patients == null ? null : patients.get(r.abhaAddress());
            m.put("uuid", r.uuid());
            m.put("abhaAddress", r.abhaAddress());
            m.put("patientName", patient == null ? null : patient.name());
            m.put("abhaNumber", patient == null ? null : AbdmFlows.maskedAbhaNumber(patient.abhaNumber()));
            m.put("consentRequestId", r.consentRequestId());
            m.put("purposeCode", r.purposeCode());
            m.put("hiTypes", r.hiTypes() == null ? List.of() : java.util.Arrays.stream(r.hiTypes().split(",")).map(String::trim)
                    .filter(t -> !t.isEmpty()).collect(Collectors.toList()));
            m.put("from", AbdmFlows.iso(r.dateFrom()));
            m.put("to", AbdmFlows.iso(r.dateTo()));
            m.put("expiry", AbdmFlows.iso(r.dataEraseAt()));
            m.put("status", r.status());
            m.put("statusHistory", r.consentRequestId() == null ? List.of() : history.getOrDefault(r.consentRequestId(), List.of()));
            m.put("error", r.error());
            m.put("createdAt", AbdmFlows.iso(r.createdAt()));
            List<Map<String, Object>> list = new ArrayList<>();
            for (AbdmHiuStore.Artefact a : r.consentRequestId() == null ? List.<AbdmHiuStore.Artefact>of()
                    : artefacts.getOrDefault(r.consentRequestId(), List.of())) {
                Map<String, Object> am = new LinkedHashMap<>();
                am.put("consentId", a.consentId());
                am.put("status", a.status());
                am.put("dataFlowStatus", a.dataFlowStatus());
                am.put("records", a.records());
                list.add(am);
            }
            m.put("artefacts", list);
            out.add(m);
        }
        return out;
    }
}
