package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmSettings;
import org.avni.server.service.abdm.AbdmTime;
import org.avni.server.service.abdm.hip.AbdmFlows;
import org.avni.server.service.abdm.hip.AbdmHipLinkingService;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.abdm.hip.AbdmNames;
import org.avni.server.service.abdm.hip.AbdmPatientPhones;
import org.avni.server.service.abdm.hip.AbdmPhoneOtp;
import org.avni.server.service.abha.AbhaEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Patient-led linking through Eka (USER_INIT_LINK_601 to 607; EKA_ABDM_SPEC.md 2.7): the patient finds the facility in
// their ABHA app, Eka asks us who they are (care_context_discover), the patient picks visits (link_init) and confirms
// with a code we send to the phone Avni holds for them (link_confirm). NHA: "every HIP must answer discovery".
// A patient is found by ABHA address, then ABHA number, each only when Eka verified the ABHA on their record, then, as
// NHA asks (USER_INIT_LINK_603), by the verified mobile narrowed by gender, year of birth and a tolerant name match
// (AbdmNames). Only one patient is ever offered: when more than one matches, none is.
// Each step is for one of the organisation's HIP IDs: the one the event names, which must be the organisation's (else the
// event is not answered), else the one the visits asked for are shared under, else its first. Only the visits shared
// under that HIP ID are offered, asked for and linked (AbdmHipLinkingService.visitsByHip); the link request keeps it, and
// the confirm links exactly the visits asked for under it, or none.
// Never logs a name, an ABHA, a phone or a code.
@Component
public class EkaHipDiscovery {
    private static final Logger logger = LoggerFactory.getLogger(EkaHipDiscovery.class);

    static final String ON_DISCOVER = "/abdm/v1/care-contexts/on-discover";
    static final String ON_INIT = "/abdm/v1/care-contexts/discover/link/on-init";
    static final String ON_CONFIRM = "/abdm/v1/care-contexts/discover/link/on-confirm";
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 3;
    private static final String MORE_THAN_ONE = "More than one patient matches at this facility; please ask the facility";

    private final AbdmSettings settings;
    private final AbdmHipStore store;
    private final AbdmHipLinkingService linking;
    private final EkaAbdmClient eka;
    private final AbdmPatientPhones phones;
    private final AbdmPhoneOtp otp;
    private final AbdmOrgContext orgContext;
    private final AbhaLinkRepository abhaLinks;
    private final AbhaEventRepository abhaEvents;

    public EkaHipDiscovery(AbdmSettings settings, AbdmHipStore store, AbdmHipLinkingService linking, EkaAbdmClient eka,
                           AbdmPatientPhones phones, AbdmPhoneOtp otp, AbdmOrgContext orgContext, AbhaLinkRepository abhaLinks,
                           AbhaEventRepository abhaEvents) {
        this.orgContext = orgContext;
        this.abhaLinks = abhaLinks;
        this.abhaEvents = abhaEvents;
        this.settings = settings;
        this.store = store;
        this.linking = linking;
        this.eka = eka;
        this.phones = phones;
        this.otp = otp;
    }

    // ---- 1: abha.care_context_discover -> on-discover

    public void discover(JsonNode event) {
        JsonNode data = dataOf(event);
        EkaSystemConfig orgSettings = settings.current();
        String named = namedHipId(data);
        if (notOurs(named, orgSettings, "discovery")) return;
        ObjectNode reply = AbdmJson.obj();
        reply.put("request_id", AbdmJson.text(data, "request_id"));
        reply.put("txn_id", AbdmJson.text(data, "txn_id"));
        ArrayNode patients = reply.putArray("patients");
        Match match = find(data, orgSettings.getAbhaAddressSuffix());
        String hipId = orgSettings.hipIdOr(named);
        if (match.subject() == null) {
            reply.set("error", error(REFUSED, match.why()));
        } else {
            AbdmHipLinkingService.Patient p = linking.loadPatient(match.subject().uuid());
            List<AbdmHipLinkingService.Visit> here = AbdmHipLinkingService.visitsByHip(p, orgSettings, store).getOrDefault(hipId, List.of());
            store.ensureCareContexts(p.individualId(), hipId, p.uuid(), AbdmHipLinkingService.HI_TYPE,
                    here.stream().map(v -> new AbdmHipStore.NewCareContext(v.reference(), v.display())).toList());
            List<AbdmHipStore.CareContext> unlinked = store.findCareContexts(p.individualId(), hipId).stream()
                    .filter(c -> !AbdmHipLinkingService.LINKED.equals(c.status()))
                    .collect(Collectors.toList());
            if (unlinked.isEmpty()) {
                reply.set("error", error(REFUSED, "No visits left to link at this facility"));
            } else {
                patients.add(patientEntry(p.uuid(), p.name(), unlinked));
            }
        }
        send(ON_DISCOVER, reply, data, hipId);
    }

    private record Match(AbdmHipStore.Subject subject, String why) {
    }

    private Match find(JsonNode data, String abhaAddressSuffix) {
        String abhaAddress = AbdmFlows.firstNonNull(AbdmJson.text(data, "abha_address"), identifier(data, "ABHAADDRESS"));
        if (abhaAddress != null) {
            Match m = one(verified(store.findSubjectsByAbhaAddress(abhaAddress, abhaAddressSuffix),
                    record -> AbdmFlows.namesAbha(abhaAddress, record.getAbhaAddress(), record.getAbhaNumber())));
            if (m != null) return m;
        }
        String number = AbdmFlows.digitsOnly(identifier(data, "ABHANUMBER"));
        if (number.length() == 14) {
            Match m = one(verified(store.findSubjectsByAbhaNumber(number), record -> number.equals(AbdmFlows.digitsOnly(record.getAbhaNumber()))));
            if (m != null) return m;
        }
        String name = AbdmJson.text(data, "patient_name");
        String gender = AbdmJson.text(data, "gender");
        Integer year = data != null && data.path("year_of_birth").canConvertToInt() ? data.path("year_of_birth").asInt() : null;
        String mobile = AbdmFlows.digitsOnly(identifier(data, "MOBILE"));
        if (!StringUtils.hasText(name) || !StringUtils.hasText(gender) || year == null || mobile.length() < 10) {
            return new Match(null, "No patient with this ABHA at this facility");
        }
        // The mobile ABDM verified is the anchor, compared with the phone Avni texts the code to.
        List<AbdmHipStore.Candidate> onTheMobile = store.findSubjectsByPhone(phones.phoneConcepts(), mobile.substring(mobile.length() - 10),
                gender.trim().substring(0, 1).toUpperCase(Locale.ROOT), year);
        if (onTheMobile.size() > AbdmHipStore.PHONE_SEARCH_LIMIT) return new Match(null, MORE_THAN_ONE);
        List<AbdmHipStore.Subject> named = onTheMobile.stream().filter(c -> AbdmNames.sameName(c.name(), name))
                .map(c -> new AbdmHipStore.Subject(c.id(), c.uuid(), c.name())).collect(Collectors.toList());
        Match m = one(named);
        return m != null ? m : new Match(null, "No patient found at this facility");
    }

    // Only subjects whose saved ABHA Eka verified (its record carries an Eka ID), and only when the identifier asked is that
    // record's own: the record's ABHA answers are typed, and a typed one nobody checked, a digit off, would hand this
    // patient's name and visits to whoever holds the ABHA typed.
    private List<AbdmHipStore.Subject> verified(List<AbdmHipStore.Subject> subjects, java.util.function.Predicate<AbhaEvent> asked) {
        return subjects.stream().filter(s -> orgContext.inReadOnlyTransaction(() -> abhaLinks.findByIndividualIdAndIsVoidedFalse(s.id())
                .flatMap(link -> AbhaEventService.ofSavedAbha(abhaEvents.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc(s.uuid()),
                        link.getAbhaNumber(), link.getAbhaAddress()))
                .filter(asked)
                .isPresent())).toList();
    }

    // Exactly one: a Match. Several: a refusal. None: null, so the next way of finding is tried.
    private static Match one(List<AbdmHipStore.Subject> subjects) {
        if (subjects == null || subjects.isEmpty()) return null;
        if (subjects.size() > 1) return new Match(null, MORE_THAN_ONE);
        return new Match(subjects.get(0), null);
    }

    // ---- 2: abha.care_context_discover_link_init -> on-init

    public void linkInit(JsonNode event) {
        JsonNode data = dataOf(event);
        EkaSystemConfig orgSettings = settings.current();
        String named = namedHipId(data);
        if (notOurs(named, orgSettings, "link init")) return;
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
        String hipId = orgSettings.hipIdOr(named);
        if (subject.isEmpty() || references.isEmpty()) {
            problem = "Patient or visits not found at this facility";
        } else {
            AbdmHipLinkingService.Patient p = linking.loadPatient(subject.get().uuid());
            Map<String, List<AbdmHipLinkingService.Visit>> byHip = AbdmHipLinkingService.visitsByHip(p, orgSettings, store);
            hipId = hipFor(named, byHip, references, orgSettings);
            if (!referencesOf(byHip, hipId).containsAll(references)) problem = "A visit asked for is not this patient's at this facility";
        }
        Optional<String> phone = problem == null ? phones.phoneOf(subject.get().id()) : Optional.empty();
        if (problem == null && phone.isEmpty()) problem = "This facility holds no phone number to send the code to";
        if (problem == null && !otp.send(phone.get())) problem = "The code could not be sent; please try again";
        if (problem != null) {
            reply.set("error", error(REFUSED, problem));
        } else {
            String linkReference = UUID.randomUUID().toString();
            Instant expiry = Instant.now().plus(CODE_LIFETIME);
            store.insertLinkRequest(transactionId, linkReference, subject.get().id(), AbdmJson.text(data, "abha_address"), references,
                    "OTP_SENT", expiry, hipId);
            reply.put("ref_num", linkReference);
            reply.put("otp_expiry", AbdmTime.format(expiry));
            logger.info("Eka discovery link {}: code sent for {} visit(s)", linkReference, references.size());
        }
        send(ON_INIT, reply, data, hipId);
    }

    // ---- 3: abha.context_discover_link_confirm -> on-confirm

    public void linkConfirm(JsonNode event) {
        JsonNode data = dataOf(event);
        EkaSystemConfig orgSettings = settings.current();
        String named = namedHipId(data);
        if (notOurs(named, orgSettings, "link confirm")) return;
        String linkReference = AbdmFlows.firstNonNull(AbdmJson.text(data, "linkRefNumber"), AbdmJson.text(data, "link_ref_number"));
        String code = AbdmJson.text(data, "token");
        ObjectNode reply = AbdmJson.obj();
        reply.put("request_id", AbdmJson.text(data, "request_id"));
        ArrayNode patients = reply.putArray("patients");
        Optional<AbdmHipStore.LinkRequest> found = store.findLinkRequest(linkReference);
        if (found.isEmpty() || found.get().individualId() == null || !"OTP_SENT".equals(found.get().status())) {
            reply.set("error", error(REFUSED, "Unknown or finished link request"));
        } else if (found.get().expiresAt() != null && Instant.now().isAfter(found.get().expiresAt())) {
            store.setLinkRequestStatus(found.get().id(), "EXPIRED");
            reply.set("error", error(REFUSED, "The code has expired"));
        } else {
            AbdmHipStore.LinkRequest request = found.get();
            Optional<AbdmHipStore.Subject> subject = store.findSubjectById(request.individualId());
            Optional<String> phone = subject.flatMap(s -> phones.phoneOf(s.id()));
            if (subject.isEmpty() || phone.isEmpty() || code == null || !otp.verify(phone.get(), code)) {
                int attempts = store.countLinkAttempt(request.id());
                if (attempts >= MAX_ATTEMPTS) store.setLinkRequestStatus(request.id(), "FAILED");
                reply.set("error", error(REFUSED, attempts >= MAX_ATTEMPTS ? "Too many wrong codes; start again" : "The code is not right"));
            } else {
                AbdmHipLinkingService.Patient p = linking.loadPatient(subject.get().uuid());
                Set<String> asked = Set.copyOf(request.careContextRefs());
                Map<String, List<AbdmHipLinkingService.Visit>> byHip = AbdmHipLinkingService.visitsByHip(p, orgSettings, store);
                String hipId = request.hipId() != null ? request.hipId() : hipFor(named, byHip, asked, orgSettings);
                // Exactly the visits asked for, under the centre the code was sent for: never fewer, never another centre.
                if ((named != null && !named.equals(hipId)) || !referencesOf(byHip, hipId).containsAll(asked)) {
                    store.setLinkRequestStatus(request.id(), "FAILED");
                    reply.set("error", error(REFUSED, "The visits asked for are no longer all at this facility; please start again"));
                    send(ON_CONFIRM, reply, data, hipId);
                    return;
                }
                List<AbdmHipStore.NewCareContext> theirs = byHip.getOrDefault(hipId, List.of()).stream().filter(v -> asked.contains(v.reference()))
                        .map(v -> new AbdmHipStore.NewCareContext(v.reference(), v.display())).collect(Collectors.toList());
                store.ensureCareContexts(p.individualId(), hipId, p.uuid(), AbdmHipLinkingService.HI_TYPE, theirs);
                List<String> refs = theirs.stream().map(AbdmHipStore.NewCareContext::reference).collect(Collectors.toList());
                List<AbdmHipStore.CareContext> rows = store.findCareContextsByReferences(hipId, refs);
                patients.add(patientEntry(p.uuid(), p.name(), rows));
                // Calling on-confirm is what links them (EKA_ABDM_SPEC.md 2.7), so Avni records the outcome only after it.
                switch (send(ON_CONFIRM, reply, data, hipId)) {
                    case ACCEPTED -> {
                        store.markLinked(hipId, refs, "USER");
                        store.setLinkRequestStatus(request.id(), "LINKED");
                        logger.info("Eka discovery link {}: {} visit(s) linked by the patient", linkReference, refs.size());
                    }
                    case UNANSWERED -> {
                        // Eka may have linked them: shareable on a data fetch and offered again, never shown LINKED. Only visits
                        // still PENDING when the update runs change; one linked, queued or failed meanwhile keeps its status.
                        store.markLinkRequestedIfPending(hipId, refs, "Eka gave no clear answer to on-confirm; it may have linked them");
                        store.setLinkRequestStatus(request.id(), "UNANSWERED");
                    }
                    case REFUSED -> store.setLinkRequestStatus(request.id(), "FAILED");
                }
                return;
            }
        }
        send(ON_CONFIRM, reply, data, orgSettings.hipIdOr(named));
    }

    private static String namedHipId(JsonNode data) {
        String named = AbdmFlows.firstNonNull(AbdmJson.text(data, "hip_id"), AbdmJson.text(data, "hipId"));
        return named == null ? null : named.trim();
    }

    // An event for a HIP ID that is not the organisation's is not answered: Avni never speaks for another's centre.
    private static boolean notOurs(String named, EkaSystemConfig orgSettings, String step) {
        if (named == null || orgSettings.hasHipId(named)) return false;
        logger.warn("Eka {} names a HIP ID that is not this organisation's; not answered", step);
        return true;
    }

    private static Set<String> referencesOf(Map<String, List<AbdmHipLinkingService.Visit>> byHip, String hipId) {
        return byHip.getOrDefault(hipId, List.of()).stream().map(AbdmHipLinkingService.Visit::reference).collect(Collectors.toSet());
    }

    // The HIP ID named, else the one the first visit asked for is shared under, else the organisation's first.
    private static String hipFor(String named, Map<String, List<AbdmHipLinkingService.Visit>> byHip, Collection<String> asked,
                                 EkaSystemConfig orgSettings) {
        if (named != null) return named;
        return byHip.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(v -> asked.contains(v.reference())))
                .map(Map.Entry::getKey).findFirst()
                .orElse(orgSettings.hipIdOr(null));
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

    // Replies go on the slow limit, as on-fetch does: Eka took over 10 s to answer on-fetch 3 times in 18 on 26 Oct.
    private Sent send(String path, ObjectNode reply, JsonNode data, String hipId) {
        Map<String, String> headers = new LinkedHashMap<>();
        String oid = AbdmJson.text(data, "oid");
        if (StringUtils.hasText(oid)) headers.put(EkaAbdmClient.H_PATIENT_OID, oid);
        headers.put(EkaAbdmClient.H_HIP_ID, hipId);
        try {
            eka.postSlow(path, reply, headers);
            return Sent.ACCEPTED;
        } catch (RuntimeException e) {
            logger.warn("Eka {} failed: {}", path, e.getMessage());
            return EkaAbdmClient.mayHaveActed(e) ? Sent.UNANSWERED : Sent.REFUSED;
        }
    }

    // A reply that refuses carries an error; one that succeeds carries none (28 Oct run: Eka passed a success's code 0 on to
    // ABDM, whose check refused the whole reply as "ABDM-9999 Invalid message"). ABDM accepts a code of 3 to 6 digits;
    // which code Eka sends it on as is unverified, so the message carries the reason.
    static final int REFUSED = 1000;

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
