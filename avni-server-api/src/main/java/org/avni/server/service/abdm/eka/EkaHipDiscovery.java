package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmTime;
import org.avni.server.service.abdm.hip.AbdmFlows;
import org.avni.server.service.abdm.hip.AbdmHipLinkingService;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.abdm.hip.AbdmPatientPhones;
import org.avni.server.service.abdm.hip.AbdmPhoneOtp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// DEMO-ONLY abdm-hackathon: never merged.
// Patient-led linking through Eka (USER_INIT_LINK_601 to 607; EKA_ABDM_SPEC.md 2.7): the patient finds the facility in
// their ABHA app, Eka asks us who they are (care_context_discover), the patient picks visits (link_init) and confirms
// with a code we send to the phone Avni holds for them (link_confirm). NHA: "every HIP must answer discovery".
// A patient is found by ABHA address, then ABHA number, then name, gender and year of birth with a matching phone. Only
// one patient is ever offered: when more than one matches, none is. Never logs a name, an ABHA, a phone or a code.
@Component
public class EkaHipDiscovery {
    private static final Logger logger = LoggerFactory.getLogger(EkaHipDiscovery.class);

    static final String ON_DISCOVER = "/abdm/v1/care-contexts/on-discover";
    static final String ON_INIT = "/abdm/v1/care-contexts/discover/link/on-init";
    static final String ON_CONFIRM = "/abdm/v1/care-contexts/discover/link/on-confirm";
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 3;

    private final AbdmConfig cfg;
    private final AbdmHipStore store;
    private final AbdmHipLinkingService linking;
    private final EkaAbdmClient eka;
    private final AbdmPatientPhones phones;
    private final AbdmPhoneOtp otp;

    public EkaHipDiscovery(AbdmConfig cfg, AbdmHipStore store, AbdmHipLinkingService linking, EkaAbdmClient eka,
                           AbdmPatientPhones phones, AbdmPhoneOtp otp) {
        this.cfg = cfg;
        this.store = store;
        this.linking = linking;
        this.eka = eka;
        this.phones = phones;
        this.otp = otp;
    }

    // ---- 1: abha.care_context_discover -> on-discover

    public void discover(JsonNode event) {
        JsonNode data = dataOf(event);
        ObjectNode reply = AbdmJson.obj();
        reply.put("request_id", AbdmJson.text(data, "request_id"));
        reply.put("txn_id", AbdmJson.text(data, "txn_id"));
        ArrayNode patients = reply.putArray("patients");
        Match match = find(data);
        if (match.subject() == null) {
            reply.set("error", error(1, match.why()));
        } else {
            AbdmHipLinkingService.Patient p = linking.loadPatient(match.subject().uuid());
            String hipId = cfg.getHipId();
            store.ensureCareContexts(p.individualId(), hipId, p.uuid(), AbdmHipLinkingService.HI_TYPE,
                    p.visits().stream().map(v -> new AbdmHipStore.NewCareContext(v.reference(), v.display())).toList());
            List<AbdmHipStore.CareContext> unlinked = store.findCareContexts(p.individualId(), hipId).stream()
                    .filter(c -> !AbdmHipLinkingService.LINKED.equals(c.status())).collect(Collectors.toList());
            if (unlinked.isEmpty()) {
                reply.set("error", error(1, "No visits left to link at this facility"));
            } else {
                patients.add(patientEntry(p.uuid(), p.name(), unlinked));
                reply.set("error", error(0, ""));
            }
        }
        send(ON_DISCOVER, reply, data);
    }

    private record Match(AbdmHipStore.Subject subject, String why) {
    }

    private Match find(JsonNode data) {
        String abhaAddress = AbdmFlows.firstNonNull(AbdmJson.text(data, "abha_address"), identifier(data, "ABHAADDRESS"));
        if (abhaAddress != null) {
            Match m = one(store.findSubjectsByAbhaAddress(abhaAddress, cfg.getAbhaAddressSuffix()));
            if (m != null) return m;
        }
        String number = AbdmFlows.digitsOnly(identifier(data, "ABHANUMBER"));
        if (number.length() == 14) {
            Match m = one(store.findSubjectsByAbhaNumber(number));
            if (m != null) return m;
        }
        String name = AbdmJson.text(data, "patient_name");
        String gender = AbdmJson.text(data, "gender");
        Integer year = data != null && data.path("year_of_birth").canConvertToInt() ? data.path("year_of_birth").asInt() : null;
        String mobile = AbdmFlows.digitsOnly(identifier(data, "MOBILE"));
        if (!StringUtils.hasText(name) || gender == null || year == null || mobile.length() < 10) {
            return new Match(null, "No patient with this ABHA at this facility");
        }
        Match m = one(store.findSubjectsByDemographics(name.trim(), gender.trim().substring(0, 1).toUpperCase(Locale.ROOT), year));
        if (m == null || m.subject() == null) return m != null ? m : new Match(null, "No patient found at this facility");
        // Conservative: a name match counts only when the phone Avni holds is the patient's.
        String held = AbdmFlows.digitsOnly(phones.phoneOf(m.subject().id()).orElse(""));
        if (held.length() < 10 || !held.substring(held.length() - 10).equals(mobile.substring(mobile.length() - 10))) {
            return new Match(null, "No patient found at this facility");
        }
        return m;
    }

    // Exactly one: a Match. Several: a refusal. None: null, so the next way of finding is tried.
    private static Match one(List<AbdmHipStore.Subject> subjects) {
        if (subjects == null || subjects.isEmpty()) return null;
        if (subjects.size() > 1) return new Match(null, "More than one patient matches at this facility; please ask the facility");
        return new Match(subjects.get(0), null);
    }

    // ---- 2: abha.care_context_discover_link_init -> on-init

    public void linkInit(JsonNode event) {
        JsonNode data = dataOf(event);
        String transactionId = AbdmJson.text(data, "txn_id");
        ObjectNode reply = AbdmJson.obj();
        reply.put("request_id", AbdmJson.text(data, "request_id"));
        reply.put("txn_id", transactionId);

        String patientReference = null;
        List<String> references = new ArrayList<>();
        JsonNode patients = AbdmJson.at(data, "patient");
        if (patients != null && patients.isObject()) patients = AbdmJson.arr().add(patients);
        if (patients != null && patients.isArray()) {
            for (JsonNode p : patients) {
                if (patientReference == null) patientReference = AbdmJson.text(p, "ref_num");
                JsonNode contexts = AbdmJson.at(p, "care_contexts");
                if (contexts != null && contexts.isArray()) {
                    for (JsonNode c : contexts) {
                        String ref = c.isValueNode() ? c.asText() : AbdmFlows.firstNonNull(AbdmJson.text(c, "reference_number"), AbdmJson.text(c, "ref_num"));
                        if (StringUtils.hasText(ref) && !references.contains(ref)) references.add(ref);
                    }
                }
            }
        }
        Optional<AbdmHipStore.Subject> subject = patientReference == null ? Optional.empty() : store.findSubjectByUuid(patientReference);
        String problem = null;
        if (subject.isEmpty() || references.isEmpty()) {
            problem = "Patient or visits not found at this facility";
        } else {
            Set<String> theirs = linking.loadPatient(subject.get().uuid()).visits().stream().map(AbdmHipLinkingService.Visit::reference)
                    .collect(Collectors.toSet());
            if (!theirs.containsAll(references)) problem = "A visit asked for is not this patient's";
        }
        Optional<String> phone = problem == null ? phones.phoneOf(subject.get().id()) : Optional.empty();
        if (problem == null && phone.isEmpty()) problem = "This facility holds no phone number to send the code to";
        if (problem == null && !otp.send(phone.get())) problem = "The code could not be sent; please try again";
        if (problem != null) {
            reply.set("error", error(1, problem));
        } else {
            String linkReference = UUID.randomUUID().toString();
            Instant expiry = Instant.now().plus(CODE_LIFETIME);
            store.insertLinkRequest(transactionId, linkReference, subject.get().id(), AbdmJson.text(data, "abha_address"), references,
                    "OTP_SENT", expiry);
            reply.put("ref_num", linkReference);
            reply.put("otp_expiry", AbdmTime.format(expiry));
            reply.set("error", error(0, ""));
            logger.info("Eka discovery link {}: code sent for {} visit(s)", linkReference, references.size());
        }
        send(ON_INIT, reply, data);
    }

    // ---- 3: abha.context_discover_link_confirm -> on-confirm

    public void linkConfirm(JsonNode event) {
        JsonNode data = dataOf(event);
        String linkReference = AbdmFlows.firstNonNull(AbdmJson.text(data, "linkRefNumber"), AbdmJson.text(data, "link_ref_number"));
        String code = AbdmJson.text(data, "token");
        ObjectNode reply = AbdmJson.obj();
        reply.put("request_id", AbdmJson.text(data, "request_id"));
        ArrayNode patients = reply.putArray("patients");
        Optional<AbdmHipStore.LinkRequest> found = store.findLinkRequest(linkReference);
        if (found.isEmpty() || found.get().individualId() == null || !"OTP_SENT".equals(found.get().status())) {
            reply.set("error", error(1, "Unknown or finished link request"));
        } else if (found.get().expiresAt() != null && Instant.now().isAfter(found.get().expiresAt())) {
            store.setLinkRequestStatus(found.get().id(), "EXPIRED");
            reply.set("error", error(1, "The code has expired"));
        } else {
            AbdmHipStore.LinkRequest request = found.get();
            Optional<AbdmHipStore.Subject> subject = store.findSubjectById(request.individualId());
            Optional<String> phone = subject.flatMap(s -> phones.phoneOf(s.id()));
            if (subject.isEmpty() || phone.isEmpty() || code == null || !otp.verify(phone.get(), code)) {
                int attempts = store.countLinkAttempt(request.id());
                if (attempts >= MAX_ATTEMPTS) store.setLinkRequestStatus(request.id(), "FAILED");
                reply.set("error", error(1, attempts >= MAX_ATTEMPTS ? "Too many wrong codes; start again" : "The code is not right"));
            } else {
                AbdmHipLinkingService.Patient p = linking.loadPatient(subject.get().uuid());
                Set<String> asked = Set.copyOf(request.careContextRefs());
                List<AbdmHipStore.NewCareContext> theirs = p.visits().stream().filter(v -> asked.contains(v.reference()))
                        .map(v -> new AbdmHipStore.NewCareContext(v.reference(), v.display())).collect(Collectors.toList());
                String hipId = cfg.getHipId();
                store.ensureCareContexts(p.individualId(), hipId, p.uuid(), AbdmHipLinkingService.HI_TYPE, theirs);
                List<String> refs = theirs.stream().map(AbdmHipStore.NewCareContext::reference).collect(Collectors.toList());
                List<AbdmHipStore.CareContext> rows = store.findCareContextsByReferences(hipId, refs);
                patients.add(patientEntry(p.uuid(), p.name(), rows));
                reply.set("error", error(0, ""));
                // Calling on-confirm is what links them (EKA_ABDM_SPEC.md 2.7), so Avni records the outcome only after it.
                switch (send(ON_CONFIRM, reply, data)) {
                    case ACCEPTED -> {
                        store.markLinked(hipId, refs, "USER");
                        store.setLinkRequestStatus(request.id(), "LINKED");
                        logger.info("Eka discovery link {}: {} visit(s) linked by the patient", linkReference, refs.size());
                    }
                    case UNANSWERED -> {
                        // Eka may have linked them: shareable on a data fetch and offered again, never shown LINKED. A visit
                        // linked on save in the meantime is left as it is.
                        List<String> notLinked = rows.stream().filter(c -> !AbdmHipLinkingService.LINKED.equals(c.status()))
                                .map(AbdmHipStore.CareContext::reference).collect(Collectors.toList());
                        if (!notLinked.isEmpty()) {
                            store.markStatus(hipId, notLinked, AbdmHipLinkingService.LINK_REQUESTED, "Eka did not answer on-confirm; it may have linked them");
                        }
                        store.setLinkRequestStatus(request.id(), "UNANSWERED");
                    }
                    case REFUSED -> store.setLinkRequestStatus(request.id(), "FAILED");
                }
                return;
            }
        }
        send(ON_CONFIRM, reply, data);
    }

    // ---- small pieces

    private static ObjectNode patientEntry(String patientReference, String display, List<AbdmHipStore.CareContext> careContexts) {
        ObjectNode p = AbdmJson.obj();
        p.put("ref_num", patientReference);
        p.put("display", display);
        p.put("hi_type", AbdmHipLinkingService.HI_TYPE);
        ArrayNode list = p.putArray("care_contexts");
        for (AbdmHipStore.CareContext c : careContexts) {
            ObjectNode n = list.addObject();
            n.put("ref_num", c.reference());
            n.put("display", c.display());
        }
        return p;
    }

    private enum Sent {ACCEPTED, REFUSED, UNANSWERED}

    // Replies go on the slow limit, as on-fetch does: Eka has taken over 10 s to answer (26 Oct).
    private Sent send(String path, ObjectNode reply, JsonNode data) {
        Map<String, String> headers = new LinkedHashMap<>();
        String oid = AbdmJson.text(data, "oid");
        if (StringUtils.hasText(oid)) headers.put(EkaAbdmClient.H_PATIENT_OID, oid);
        headers.put(EkaAbdmClient.H_HIP_ID, cfg.getHipId());
        try {
            eka.postSlow(path, reply, headers);
            return Sent.ACCEPTED;
        } catch (EkaAbdmClient.NoAnswer e) {
            logger.warn("Eka {} failed: {}", path, e.getMessage());
            return e.sent ? Sent.UNANSWERED : Sent.REFUSED;
        } catch (RuntimeException e) {
            logger.warn("Eka {} failed: {}", path, e.getMessage());
            return Sent.REFUSED;
        }
    }

    private static ObjectNode error(int code, String message) {
        ObjectNode e = AbdmJson.obj();
        e.put("code", code);
        e.put("message", message);
        return e;
    }

    // The value of the first identifier of this type (types compared in letters only, any case).
    private static String identifier(JsonNode data, String type) {
        JsonNode ids = AbdmJson.at(data, "identifiers");
        if (ids == null || !ids.isArray()) return null;
        for (JsonNode i : ids) {
            String t = AbdmJson.text(i, "type");
            if (t != null && t.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "").equals(type)) return AbdmJson.text(i, "value");
        }
        return null;
    }

    private static JsonNode dataOf(JsonNode event) {
        JsonNode data = event == null ? null : event.get("data");
        return data != null && data.isObject() ? data : event;
    }
}
