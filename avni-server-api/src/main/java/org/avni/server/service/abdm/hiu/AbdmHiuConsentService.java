package org.avni.server.service.abdm.hiu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmCallbackHeaders;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.AbdmTime;
import org.avni.server.service.abdm.eka.EkaHiuService;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.avni.server.service.abdm.hip.AbdmFlows;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abdm.hip.AbdmRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// DEMO-ONLY abdm-hackathon: never merged.
// Avni as HIU: ask the patient's consent manager for consent, hear the answer, fetch each granted artefact and, once
// fetched, ask the HIP for the records (AbdmHiuDataService).
@Service
public class AbdmHiuConsentService {
    private static final Logger logger = LoggerFactory.getLogger(AbdmHiuConsentService.class);

    public static final String REQUESTED = "REQUESTED";
    public static final String GRANTED = "GRANTED";
    public static final String FAILED = "FAILED";

    private static final Map<String, String> PURPOSES = Map.of(
            "CAREMGT", "Care Management",
            "BTG", "Break the Glass",
            "PUBHLTH", "Public Health",
            "HPAYMT", "Healthcare Payment",
            "DSRCH", "Disease Specific Healthcare Research",
            "PATRQT", "Self Requested");

    private final AbdmConfig cfg;
    private final AbdmHiuStore store;
    private final AbdmGatewayClient gateway;
    private final AbdmHiuDataService dataService;
    private final AbdmRetry retry;
    private final ObjectProvider<EkaHiuService> ekaHiuService;

    public AbdmHiuConsentService(AbdmConfig cfg, AbdmHiuStore store, AbdmGatewayClient gateway, AbdmHiuDataService dataService,
                                 AbdmRetry retry, ObjectProvider<EkaHiuService> ekaHiuService) {
        this.cfg = cfg;
        this.store = store;
        this.gateway = gateway;
        this.dataService = dataService;
        this.retry = retry;
        this.ekaHiuService = ekaHiuService;
    }

    public Map<String, Object> requestConsent(String abhaAddress, LocalDate from, LocalDate to, List<String> hiTypes,
                                              String purposeCode, String requesterName) {
        // Eka mode: the same request through Eka Care; list() and the records read the same tables either way.
        if (cfg.isEka()) return ekaHiuService.getObject().requestConsent(abhaAddress, from, to, hiTypes, purposeCode, requesterName);
        String address = AbdmFlows.withSuffix(abhaAddress, cfg.getAbhaAddressSuffix());
        if (address == null) throw AbdmRequestException.badRequest("abhaAddress is required");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate fromDate = from == null ? today.minusYears(5) : from;
        LocalDate toDate = to == null ? today : to;
        if (toDate.isBefore(fromDate)) throw AbdmRequestException.badRequest("'to' is before 'from'");
        List<String> types = hiTypes == null || hiTypes.isEmpty() ? List.of("OPConsultation") : hiTypes;
        String purpose = purposeCode == null || purposeCode.isBlank() ? "CAREMGT" : purposeCode;
        String requester = requesterName == null || requesterName.isBlank() ? "Dr. Demo" : requesterName;

        Instant dateFrom = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dateTo = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
        Instant dataEraseAt = Instant.now().plus(30, ChronoUnit.DAYS);
        String uuid = UUID.randomUUID().toString();
        String requestId = AbdmGatewayClient.newRequestId();
        // Persist before send: on-init can arrive before post() returns.
        store.insertConsentRequest(uuid, address, cfg.getHiuId(), purpose, String.join(",", types), dateFrom, dateTo, dataEraseAt,
                requestId, REQUESTED);

        ObjectNode body = AbdmJson.obj();
        ObjectNode consent = body.putObject("consent");
        ObjectNode p = consent.putObject("purpose");
        p.put("text", PURPOSES.getOrDefault(purpose, purpose));
        p.put("code", purpose);
        p.put("refUri", "http://terminology.hl7.org/ValueSet/v3-PurposeOfUse");
        consent.putObject("patient").put("id", address);
        consent.putObject("hiu").put("id", cfg.getHiuId());
        ObjectNode req = consent.putObject("requester");
        req.put("name", requester);
        ObjectNode identifier = req.putObject("identifier");
        identifier.put("type", "REGNO");
        identifier.put("value", "MH1001");
        identifier.put("system", "https://www.mciindia.org");
        ArrayNode hiTypesNode = consent.putArray("hiTypes");
        types.forEach(hiTypesNode::add);
        ObjectNode permission = consent.putObject("permission");
        permission.put("accessMode", "VIEW");
        ObjectNode dateRange = permission.putObject("dateRange");
        dateRange.put("from", AbdmTime.format(dateFrom));
        dateRange.put("to", AbdmTime.format(dateTo));
        permission.put("dataEraseAt", AbdmTime.format(dataEraseAt));
        ObjectNode frequency = permission.putObject("frequency");
        frequency.put("unit", "HOUR");
        frequency.put("value", 1);
        frequency.put("repeats", 0);

        String status = REQUESTED;
        try {
            gateway.post(AbdmPaths.OUT_CONSENT_INIT, requestId, body, Map.of(AbdmPaths.H_HIU_ID, cfg.getHiuId()));
        } catch (RuntimeException e) {
            status = FAILED;
            store.findConsentRequestByInitRequest(requestId).ifPresent(c -> store.failConsent(c.id(), "Consent request failed: " + e.getMessage()));
            logger.warn("ABDM consent init {} failed: {}", uuid, e.getMessage());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", uuid);
        out.put("status", status);
        return out;
    }

    public void onInit(JsonNode body, AbdmCallbackHeaders headers) {
        String requestId = AbdmJson.requestIdOf(body);
        Optional<AbdmHiuStore.Consent> found = retry.find(() -> store.findConsentRequestByInitRequest(requestId));
        if (found.isEmpty()) {
            logger.warn("ABDM consent on-init for unknown request {}; dropped", requestId);
            return;
        }
        String error = AbdmFlows.errorOf(body);
        String consentRequestId = AbdmFlows.firstNonNull(AbdmJson.text(body, "consentRequest.id"), AbdmJson.text(body, "consentRequestId"));
        if (error != null || consentRequestId == null) {
            store.failConsent(found.get().id(), error != null ? error : "No consent request id in the gateway's answer");
            return;
        }
        store.setConsentRequestId(found.get().id(), consentRequestId, REQUESTED);
    }

    public void notify(JsonNode body, AbdmCallbackHeaders headers) {
        JsonNode notification = AbdmJson.at(body, "notification");
        String consentRequestId = AbdmJson.text(notification, "consentRequestId");
        String status = AbdmJson.text(notification, "status");
        List<String> artefacts = AbdmFlows.texts(AbdmJson.at(notification, "consentArtefacts"), "id");

        // The request row may lag a notify that overtakes on-init.
        Optional<AbdmHiuStore.Consent> request = retry.find(() -> store.findConsentRequest(consentRequestId));
        if (request.isEmpty()) logger.warn("ABDM HIU consent notify for unknown consent request {}", consentRequestId);
        if (status != null && consentRequestId != null) store.setConsentRequestStatus(consentRequestId, status);

        ObjectNode reply = AbdmJson.obj();
        ArrayNode acks = reply.putArray("acknowledgement");
        for (String consentId : artefacts) {
            ObjectNode a = acks.addObject();
            a.put("status", "ok");
            a.put("consentId", consentId);
        }
        reply.set("response", AbdmFlows.responseTo(headers.requestId()));
        try {
            gateway.post(AbdmPaths.OUT_HIU_CONSENT_ON_NOTIFY, AbdmGatewayClient.newRequestId(), reply, Map.of(AbdmPaths.H_HIU_ID, cfg.getHiuId()));
        } catch (RuntimeException e) {
            logger.warn("ABDM HIU consent on-notify for {} failed: {}", consentRequestId, e.getMessage());
        }

        if (!GRANTED.equals(status)) {
            store.setArtefactStatus(artefacts, status);
            return;
        }
        String abhaAddress = request.map(AbdmHiuStore.Consent::abhaAddress).orElse(null);
        for (String consentId : artefacts) {
            String fetchRequestId = AbdmGatewayClient.newRequestId();
            // Persist before send.
            store.upsertArtefact(consentRequestId, consentId, abhaAddress, cfg.getHiuId(), GRANTED, fetchRequestId);
            ObjectNode fetch = AbdmJson.obj();
            fetch.put("consentId", consentId);
            try {
                gateway.post(AbdmPaths.OUT_CONSENT_FETCH, fetchRequestId, fetch, Map.of(AbdmPaths.H_HIU_ID, cfg.getHiuId()));
            } catch (RuntimeException e) {
                logger.warn("ABDM consent fetch for {} failed: {}", consentId, e.getMessage());
            }
        }
    }

    public void onFetch(JsonNode body, AbdmCallbackHeaders headers) {
        String requestId = AbdmJson.requestIdOf(body);
        Optional<AbdmHiuStore.Consent> found = retry.find(() -> store.findArtefactByFetchRequest(requestId));
        if (found.isEmpty()) {
            logger.warn("ABDM consent on-fetch for unknown request {}; dropped", requestId);
            return;
        }
        AbdmHiuStore.Consent artefact = found.get();
        String error = AbdmFlows.errorOf(body);
        JsonNode detail = AbdmJson.at(body, "consent.consentDetail");
        if (error != null || detail == null || !detail.isObject()) {
            store.failConsent(artefact.id(), error != null ? error : "No consent detail in the gateway's answer");
            return;
        }
        String status = AbdmJson.text(body, "consent.status");
        store.storeArtefactDetail(artefact.id(), status, AbdmJson.text(detail, "hip.id"),
                String.join(",", AbdmFlows.texts(AbdmJson.at(detail, "hiTypes"), null)),
                AbdmTime.parse(AbdmJson.text(detail, "permission.dateRange.from")),
                AbdmTime.parse(AbdmJson.text(detail, "permission.dateRange.to")),
                AbdmTime.parse(AbdmJson.text(detail, "permission.dataEraseAt")), detail);
        if (status == null || GRANTED.equals(status)) {
            dataService.requestHealthInformation(artefact.consentId());
        }
    }

    public void onStatus(JsonNode body, AbdmCallbackHeaders headers) {
        String consentRequestId = AbdmFlows.firstNonNull(AbdmJson.text(body, "consentRequest.id"), AbdmJson.text(body, "consentRequestId"));
        String status = AbdmFlows.firstNonNull(AbdmJson.text(body, "consentRequest.status"), AbdmJson.text(body, "status"));
        if (consentRequestId == null || status == null) {
            logger.info("ABDM consent on-status without a request id or status; ignored");
            return;
        }
        store.setConsentRequestStatus(consentRequestId, status);
        List<String> artefacts = AbdmFlows.texts(AbdmJson.at(body, "consentRequest.consentArtefacts"), "id");
        if (!GRANTED.equals(status)) store.setArtefactStatus(artefacts, status);
    }

    // [{uuid, abhaAddress, consentRequestId, status, error, createdAt, artefacts:[{consentId, status, dataFlowStatus, records}]}]
    public List<Map<String, Object>> list() {
        List<AbdmHiuStore.Consent> requests = store.listConsentRequests();
        Map<String, List<AbdmHiuStore.Artefact>> artefacts = store.findArtefacts(requests.stream().map(AbdmHiuStore.Consent::consentRequestId)
                        .filter(id -> id != null).collect(Collectors.toList()))
                .stream().collect(Collectors.groupingBy(AbdmHiuStore.Artefact::consentRequestId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AbdmHiuStore.Consent r : requests) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", r.uuid());
            m.put("abhaAddress", r.abhaAddress());
            m.put("consentRequestId", r.consentRequestId());
            m.put("status", r.status());
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
