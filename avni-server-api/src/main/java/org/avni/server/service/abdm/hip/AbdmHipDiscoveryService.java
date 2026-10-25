package org.avni.server.service.abdm.hip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmCallbackHeaders;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.AbdmTime;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// DEMO-ONLY abdm-hackathon: never merged.
// User-initiated linking, from the patient's PHR app: discover finds the patient by ABHA address (or number) and offers
// the visits not yet linked; link init issues an OTP; link confirm checks it and links. DEMO-ONLY: the OTP is
// abdm.demo-otp and no SMS is sent.
@Service
public class AbdmHipDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(AbdmHipDiscoveryService.class);

    private static final Duration OTP_LIFETIME = Duration.ofMinutes(10);

    private final AbdmConfig cfg;
    private final AbdmHipStore store;
    private final AbdmGatewayClient gateway;
    private final AbdmHipLinkingService linkingService;

    public AbdmHipDiscoveryService(AbdmConfig cfg, AbdmHipStore store, AbdmGatewayClient gateway, AbdmHipLinkingService linkingService) {
        this.cfg = cfg;
        this.store = store;
        this.gateway = gateway;
        this.linkingService = linkingService;
    }

    public void discover(JsonNode body, AbdmCallbackHeaders headers) {
        String transactionId = AbdmJson.text(body, "transactionId");
        JsonNode patient = AbdmJson.at(body, "patient");
        ObjectNode reply = AbdmJson.obj();
        reply.put("transactionId", transactionId);

        String matchedBy = "ABHA_ADDRESS";
        List<AbdmHipStore.Subject> subjects = new ArrayList<>();
        String abhaAddress = AbdmJson.text(patient, "id");
        if (abhaAddress != null) subjects = store.findSubjectsByAbhaAddress(abhaAddress, cfg.getAbhaAddressSuffix());
        if (subjects.isEmpty()) {
            String number = abhaNumberOf(AbdmJson.at(patient, "verifiedIdentifiers"));
            if (number != null) {
                subjects = store.findSubjectsByAbhaNumber(number);
                matchedBy = "ABHA_NUMBER";
            }
        }

        if (subjects.isEmpty()) {
            logger.info("ABDM discover {}: no patient matched", transactionId);
            reply.set("error", AbdmFlows.error("PATIENT_NOT_FOUND", "No patient found at this HIP"));
        } else {
            AbdmHipLinkingService.Patient p = linkingService.loadPatient(subjects.get(0).uuid());
            String hipId = cfg.getHipId();
            store.ensureCareContexts(p.individualId(), hipId, p.uuid(), AbdmHipLinkingService.HI_TYPE,
                    p.visits().stream().map(v -> new AbdmHipStore.NewCareContext(v.reference(), v.display())).toList());
            List<AbdmHipStore.CareContext> unlinked = store.findCareContexts(p.individualId(), hipId).stream()
                    .filter(c -> !AbdmHipLinkingService.LINKED.equals(c.status())).collect(Collectors.toList());
            if (unlinked.isEmpty()) {
                reply.set("error", AbdmFlows.error("CARE_CONTEXTS_NOT_FOUND", "No visits left to link"));
            } else {
                reply.set("patient", AbdmJson.arr().add(AbdmHipLinkingService.patientEntry(p.uuid(), p.name(), unlinked)));
                reply.set("matchedBy", AbdmJson.arr().add(matchedBy));
            }
        }
        reply.set("response", AbdmFlows.responseTo(headers.requestId()));
        send(AbdmPaths.OUT_ON_DISCOVER, reply, transactionId);
    }

    public void linkInit(JsonNode body, AbdmCallbackHeaders headers) {
        String transactionId = AbdmJson.text(body, "transactionId");
        String abhaAddress = AbdmJson.text(body, "abhaAddress");
        String patientReference = null;
        List<String> references = new ArrayList<>();
        JsonNode patients = AbdmJson.at(body, "patient");
        if (patients != null && patients.isObject()) patients = AbdmJson.arr().add(patients);
        if (patients != null && patients.isArray()) {
            for (JsonNode p : patients) {
                if (patientReference == null) patientReference = AbdmJson.text(p, "referenceNumber");
                references.addAll(AbdmFlows.texts(AbdmJson.at(p, "careContexts"), "referenceNumber"));
            }
        }

        ObjectNode reply = AbdmJson.obj();
        reply.put("transactionId", transactionId);
        Optional<AbdmHipStore.Subject> subject = patientReference == null ? Optional.empty() : store.findSubjectByUuid(patientReference);
        if (subject.isEmpty() || references.isEmpty()) {
            reply.set("error", AbdmFlows.error("PATIENT_NOT_FOUND", "Patient or visits not found at this HIP"));
        } else {
            String linkReference = UUID.randomUUID().toString();
            Instant expiry = Instant.now().plus(OTP_LIFETIME);
            store.insertLinkRequest(transactionId, linkReference, subject.get().id(), abhaAddress, references, "OTP_SENT", expiry);
            logger.info("ABDM link request {}: OTP issued (demo)", linkReference);
            ObjectNode link = reply.putObject("link");
            link.put("referenceNumber", linkReference);
            link.put("authenticationType", "DIRECT");
            ObjectNode meta = link.putObject("meta");
            meta.put("communicationMedium", "MOBILE");
            meta.put("communicationHint", "OTP");
            meta.put("communicationExpiry", AbdmTime.format(expiry));
        }
        reply.set("response", AbdmFlows.responseTo(headers.requestId()));
        send(AbdmPaths.OUT_LINK_ON_INIT, reply, transactionId);
    }

    public void linkConfirm(JsonNode body, AbdmCallbackHeaders headers) {
        String linkReference = AbdmJson.text(body, "confirmation.linkRefNumber");
        String token = AbdmJson.text(body, "confirmation.token");
        ObjectNode reply = AbdmJson.obj();
        Optional<AbdmHipStore.LinkRequest> found = store.findLinkRequest(linkReference);
        if (found.isEmpty() || found.get().individualId() == null) {
            reply.set("error", AbdmFlows.error("LINK_REFERENCE_NOT_FOUND", "Unknown link reference"));
        } else if (found.get().expiresAt() != null && Instant.now().isAfter(found.get().expiresAt())) {
            store.setLinkRequestStatus(found.get().id(), "EXPIRED");
            reply.set("error", AbdmFlows.error("OTP_EXPIRED", "The OTP has expired"));
        } else if (token == null || !token.equals(cfg.getDemoOtp())) {
            reply.set("error", AbdmFlows.error("OTP_INVALID", "The OTP is not valid"));
        } else {
            AbdmHipStore.LinkRequest request = found.get();
            AbdmHipStore.Subject subject = store.findSubjectById(request.individualId()).orElse(null);
            if (subject == null) {
                reply.set("error", AbdmFlows.error("PATIENT_NOT_FOUND", "Patient not found at this HIP"));
            } else {
                AbdmHipLinkingService.Patient p = linkingService.loadPatient(subject.uuid());
                Set<String> asked = Set.copyOf(request.careContextRefs());
                List<AbdmHipStore.NewCareContext> theirs = p.visits().stream().filter(v -> asked.contains(v.reference()))
                        .map(v -> new AbdmHipStore.NewCareContext(v.reference(), v.display())).collect(Collectors.toList());
                String hipId = cfg.getHipId();
                store.ensureCareContexts(p.individualId(), hipId, p.uuid(), AbdmHipLinkingService.HI_TYPE, theirs);
                List<String> refs = theirs.stream().map(AbdmHipStore.NewCareContext::reference).collect(Collectors.toList());
                store.markLinked(hipId, refs, "USER");
                store.setLinkRequestStatus(request.id(), "LINKED");
                reply.set("patient", AbdmJson.arr().add(AbdmHipLinkingService.patientEntry(p.uuid(), p.name(),
                        store.findCareContextsByReferences(hipId, refs))));
            }
        }
        reply.set("response", AbdmFlows.responseTo(headers.requestId()));
        send(AbdmPaths.OUT_LINK_ON_CONFIRM, reply, linkReference);
    }

    private void send(String path, ObjectNode reply, String correlation) {
        try {
            gateway.post(path, AbdmGatewayClient.newRequestId(), reply, Map.of(AbdmPaths.H_HIP_ID, cfg.getHipId()));
        } catch (RuntimeException e) {
            logger.warn("ABDM {} for {} failed: {}", path, correlation, e.getMessage());
        }
    }

    // The ABHA number among the verified identifiers, digits only.
    private static String abhaNumberOf(JsonNode identifiers) {
        if (identifiers == null || !identifiers.isArray()) return null;
        for (JsonNode i : identifiers) {
            String type = AbdmJson.text(i, "type");
            if (type == null) continue;
            String t = type.toUpperCase(Locale.ENGLISH);
            if (t.contains("ABHA_NUMBER") || t.contains("HEALTH_NUMBER") || t.contains("HEALTH_ID_NUMBER")) {
                String digits = AbdmFlows.digitsOnly(AbdmJson.text(i, "value"));
                if (digits.length() == 14) return digits;
            }
        }
        return null;
    }
}
