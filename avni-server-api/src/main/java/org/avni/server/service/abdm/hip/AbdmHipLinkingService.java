package org.avni.server.service.abdm.hip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.AbhaLink;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.service.abdm.AbdmCallbackHeaders;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.eka.EkaHipService;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// DEMO-ONLY abdm-hackathon: never merged.
// HIP-initiated linking: a subject's completed visits become care contexts on their ABHA. Ask the gateway for a link
// token (once per patient), then link the care contexts with it. Statuses: PENDING -> LINK_REQUESTED -> LINKED | FAILED.
@Service
public class AbdmHipLinkingService {
    private static final Logger logger = LoggerFactory.getLogger(AbdmHipLinkingService.class);

    public static final String HI_TYPE = "OPConsultation";
    public static final String PENDING = "PENDING";
    public static final String LINK_REQUESTED = "LINK_REQUESTED";
    public static final String LINKED = "LINKED";
    public static final String FAILED = "FAILED";

    // DEMO-ONLY: dates shown to the patient in India's time.
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormat.forPattern("d MMM yyyy").withLocale(Locale.ENGLISH)
            .withZone(DateTimeZone.forID("Asia/Kolkata"));

    private final AbdmConfig cfg;
    private final AbdmHipStore store;
    private final AbdmGatewayClient gateway;
    private final AbdmOrgContext orgContext;
    private final IndividualRepository individualRepository;
    private final AbhaLinkRepository abhaLinkRepository;
    private final EkaCredentialCipher cipher;
    private final AbdmRetry retry;
    // Eka mode links through Eka instead. Lazy: EkaHipService reuses this service's patient loading and view.
    private final ObjectProvider<EkaHipService> ekaHipService;

    public AbdmHipLinkingService(AbdmConfig cfg, AbdmHipStore store, AbdmGatewayClient gateway, AbdmOrgContext orgContext,
                                 IndividualRepository individualRepository, AbhaLinkRepository abhaLinkRepository,
                                 EkaCredentialCipher cipher, AbdmRetry retry, ObjectProvider<EkaHipService> ekaHipService) {
        this.cfg = cfg;
        this.store = store;
        this.gateway = gateway;
        this.orgContext = orgContext;
        this.individualRepository = individualRepository;
        this.abhaLinkRepository = abhaLinkRepository;
        this.cipher = cipher;
        this.retry = retry;
        this.ekaHipService = ekaHipService;
    }

    // One visit that can be shared: an encounter's uuid, its display and when it happened.
    public record Visit(String reference, String display, Instant at) {
    }

    // A subject as linking needs it, read in one read-only transaction.
    public record Patient(long individualId, String uuid, String name, String gender, Integer yearOfBirth, String abhaNumber,
                          String abhaAddress, List<Visit> visits) {
    }

    // ---- web

    public List<Map<String, Object>> patientsWithAbha() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AbdmHipStore.PatientRow r : store.patientsWithAbha(cfg.getHipId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("subjectUUID", r.subjectUuid());
            m.put("name", r.name());
            m.put("abhaNumberMasked", AbdmFlows.maskedAbhaNumber(r.abhaNumber()));
            m.put("abhaAddress", AbdmFlows.withSuffix(r.abhaAddress(), cfg.getAbhaAddressSuffix()));
            m.put("encounters", r.encounters());
            m.put("linked", r.linked());
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> careContexts(String subjectUuid) {
        return view(loadPatient(subjectUuid));
    }

    // Links every completed visit not yet linked; answers at once with the statuses, the gateway answers later.
    public List<Map<String, Object>> linkCareContexts(String subjectUuid) {
        if (cfg.isEka()) return ekaHipService.getObject().linkCareContexts(subjectUuid);
        Patient patient = loadPatient(subjectUuid);
        if (patient.abhaNumber() == null) {
            throw AbdmRequestException.conflict("This subject has no ABHA number");
        }
        String hipId = cfg.getHipId();
        store.ensureCareContexts(patient.individualId(), hipId, patient.uuid(), HI_TYPE,
                patient.visits().stream().map(v -> new AbdmHipStore.NewCareContext(v.reference(), v.display())).toList());
        List<AbdmHipStore.CareContext> toLink = notLinked(store.findCareContexts(patient.individualId(), hipId));
        if (toLink.isEmpty()) return view(patient);

        Optional<AbdmHipStore.PatientLink> link = store.findPatientLink(patient.individualId(), hipId);
        String linkToken = link.map(AbdmHipStore.PatientLink::encryptedLinkToken).map(this::readLinkToken).orElse(null);
        if (linkToken != null) {
            sendLink(new AbdmHipStore.Subject(patient.individualId(), patient.uuid(), patient.name()), patient.abhaNumber(),
                    patient.abhaAddress(), toLink, linkToken);
        } else {
            requestLinkToken(patient, refs(toLink));
        }
        return view(patient);
    }

    // A stored token we cannot read is treated as none: a new one is asked for.
    private String readLinkToken(String encrypted) {
        try {
            return cipher.decrypt(encrypted);
        } catch (IllegalStateException e) {
            logger.warn("ABDM stored link token unreadable: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    // ---- callbacks

    public void onGenerateToken(JsonNode body, AbdmCallbackHeaders headers) {
        String requestId = AbdmJson.requestIdOf(body);
        Optional<AbdmHipStore.PatientLink> found = retry.find(() -> store.findPatientLinkByTokenRequest(requestId));
        if (found.isEmpty()) {
            logger.warn("ABDM on-generate-token for unknown request {}; dropped", requestId);
            return;
        }
        AbdmHipStore.PatientLink link = found.get();
        List<AbdmHipStore.CareContext> pending = notLinked(store.findCareContexts(link.individualId(), link.hipId()));
        String error = AbdmFlows.errorOf(body);
        String linkToken = AbdmJson.text(body, "linkToken");
        if (error != null || linkToken == null || linkToken.isBlank()) {
            String reason = error != null ? error : "No link token in the gateway's answer";
            store.failPatientLink(link.id(), reason);
            store.markStatus(link.hipId(), refs(pending), FAILED, reason);
            logger.info("ABDM link token refused for patient link {}: {}", link.id(), reason);
            return;
        }
        try {
            store.storeLinkToken(link.id(), cipher.encrypt(linkToken)); // DEMO-ONLY reuse of the Eka key
        } catch (IllegalStateException e) {
            // No Eka key on this server: link now, ask for a token again next time.
            logger.warn("ABDM link token for patient link {} not kept: {}", link.id(), e.getClass().getSimpleName());
        }
        if (pending.isEmpty()) return;
        Optional<AbdmHipStore.Subject> subject = store.findSubjectById(link.individualId());
        if (subject.isEmpty()) {
            logger.warn("ABDM link token received for patient link {} whose subject is gone", link.id());
            return;
        }
        sendLink(subject.get(), link.abhaNumber(), link.abhaAddress(), pending, linkToken);
    }

    public void onLinkCareContext(JsonNode body, AbdmCallbackHeaders headers) {
        String requestId = AbdmJson.requestIdOf(body);
        Optional<List<AbdmHipStore.CareContext>> found = retry.find(() -> {
            List<AbdmHipStore.CareContext> rows = store.findCareContextsByLinkRequest(requestId);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows);
        });
        if (found.isEmpty()) {
            logger.warn("ABDM on_carecontext for unknown request {}; dropped", requestId);
            return;
        }
        AbdmHipStore.CareContext first = found.get().get(0);
        String error = AbdmFlows.errorOf(body);
        if (error != null) {
            store.markByLinkRequest(requestId, FAILED, error);
            // DEMO-ONLY: assume the token was the problem, so the next attempt asks for a new one.
            store.clearLinkToken(first.individualId(), first.hipId());
            logger.info("ABDM care context link {} refused: {}", requestId, error);
            return;
        }
        store.markByLinkRequest(requestId, LINKED, null);
        store.setPatientLinkStatus(first.individualId(), first.hipId(), LINKED);
    }

    // ---- sending

    private void requestLinkToken(Patient patient, List<String> references) {
        String hipId = cfg.getHipId();
        String abhaAddress = AbdmFlows.withSuffix(patient.abhaAddress(), cfg.getAbhaAddressSuffix());
        String requestId = AbdmGatewayClient.newRequestId();
        // Persist before send: the callback can arrive before post() returns.
        store.requestToken(patient.individualId(), hipId, patient.abhaNumber(), abhaAddress, requestId);
        store.markLinkRequested(hipId, references, null);

        ObjectNode body = AbdmJson.obj();
        body.put("abhaNumber", AbdmFlows.hyphenatedAbhaNumber(patient.abhaNumber()));
        if (abhaAddress != null) body.put("abhaAddress", abhaAddress);
        body.put("name", patient.name());
        body.put("gender", patient.gender());
        if (patient.yearOfBirth() != null) body.put("yearOfBirth", patient.yearOfBirth());
        try {
            gateway.post(AbdmPaths.OUT_GENERATE_TOKEN, requestId, body, Map.of(AbdmPaths.H_HIP_ID, hipId));
        } catch (RuntimeException e) {
            String reason = "Link token request failed: " + e.getMessage();
            store.findPatientLink(patient.individualId(), hipId).ifPresent(l -> store.failPatientLink(l.id(), reason));
            store.markStatus(hipId, references, FAILED, reason);
            logger.warn("ABDM generate-token for subject {} failed: {}", patient.uuid(), e.getMessage());
        }
    }

    private void sendLink(AbdmHipStore.Subject subject, String abhaNumber, String abhaAddress,
                          List<AbdmHipStore.CareContext> careContexts, String linkToken) {
        String hipId = cfg.getHipId();
        String requestId = AbdmGatewayClient.newRequestId();
        List<String> references = refs(careContexts);
        // Persist before send.
        store.markLinkRequested(hipId, references, requestId);

        ObjectNode body = AbdmJson.obj();
        body.put("abhaNumber", AbdmFlows.hyphenatedAbhaNumber(abhaNumber));
        String address = AbdmFlows.withSuffix(abhaAddress, cfg.getAbhaAddressSuffix());
        if (address != null) body.put("abhaAddress", address);
        body.set("patient", AbdmJson.arr().add(patientEntry(subject.uuid(), subject.name(), careContexts)));
        Map<String, String> headers = new HashMap<>();
        headers.put(AbdmPaths.H_HIP_ID, hipId);
        headers.put(AbdmPaths.H_LINK_TOKEN, linkToken);
        try {
            gateway.post(AbdmPaths.OUT_LINK_CARE_CONTEXT, requestId, body, headers);
        } catch (RuntimeException e) {
            store.markByLinkRequest(requestId, FAILED, "Link request failed: " + e.getMessage());
            logger.warn("ABDM link care contexts for subject {} failed: {}", subject.uuid(), e.getMessage());
        }
    }

    // {referenceNumber, display, careContexts:[{referenceNumber, display}], hiType, count}: one patient, one hiType.
    static ObjectNode patientEntry(String patientReference, String patientName, List<AbdmHipStore.CareContext> careContexts) {
        ObjectNode p = AbdmJson.obj();
        p.put("referenceNumber", patientReference);
        p.put("display", patientName);
        ArrayNode list = p.putArray("careContexts");
        for (AbdmHipStore.CareContext c : careContexts) {
            ObjectNode cc = list.addObject();
            cc.put("referenceNumber", c.reference());
            cc.put("display", c.display());
        }
        p.put("hiType", HI_TYPE);
        p.put("count", careContexts.size());
        return p;
    }

    // ---- reading

    public Patient loadPatient(String subjectUuid) {
        Patient patient = orgContext.inReadOnlyTransaction(() -> {
            Individual individual = individualRepository.findByUuid(subjectUuid);
            if (individual == null || individual.isVoided()) return null;
            return toPatient(individual, abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(individual.getId()).orElse(null));
        });
        if (patient == null) throw AbdmRequestException.notFound("Subject not found");
        return patient;
    }

    // Call inside a transaction: encounters are lazy.
    public static Patient toPatient(Individual individual, AbhaLink abhaLink) {
        Integer yearOfBirth = individual.getDateOfBirth() == null ? null : individual.getDateOfBirth().getYear();
        return new Patient(individual.getId(), individual.getUuid(), individual.getFullName(), genderLetter(individual.getGenderName()),
                yearOfBirth, abhaLink == null ? null : abhaLink.getAbhaNumber(), abhaLink == null ? null : abhaLink.getAbhaAddress(),
                visits(individual));
    }

    // Completed, not cancelled, not voided; newest first.
    public static List<Visit> visits(Individual individual) {
        return individual.nonVoidedEncounters()
                .filter(e -> e.getEncounterDateTime() != null && e.getCancelDateTime() == null)
                .sorted(Comparator.comparing((Encounter e) -> e.getEncounterDateTime()).reversed())
                .map(e -> new Visit(e.getUuid(), display(e), e.getEncounterDateTime().toDate().toInstant()))
                .collect(Collectors.toList());
    }

    public static String display(Encounter encounter) {
        String type = encounter.getEncounterType() == null ? "Visit" : encounter.getEncounterType().getName();
        return type + ", " + DISPLAY_DATE.print(encounter.getEncounterDateTime());
    }

    static String genderLetter(String genderName) {
        if (genderName == null) return "O";
        String g = genderName.trim().toLowerCase(Locale.ENGLISH);
        if (g.startsWith("m")) return "M";
        if (g.startsWith("f")) return "F";
        return "O";
    }

    // Every visit with its link state, plus rows whose visit has since gone.
    private List<Map<String, Object>> view(Patient patient) {
        Map<String, AbdmHipStore.CareContext> rows = new LinkedHashMap<>();
        for (AbdmHipStore.CareContext c : store.findCareContexts(patient.individualId(), cfg.getHipId())) rows.put(c.reference(), c);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Visit v : patient.visits()) {
            AbdmHipStore.CareContext row = rows.remove(v.reference());
            out.add(row != null ? view(row) : view(v.reference(), v.display(), HI_TYPE, PENDING, null, null));
        }
        for (AbdmHipStore.CareContext row : rows.values()) out.add(view(row));
        return out;
    }

    private static Map<String, Object> view(AbdmHipStore.CareContext c) {
        return view(c.reference(), c.display(), c.hiType(), c.status(), c.error(), c.updatedAt());
    }

    private static Map<String, Object> view(String reference, String display, String hiType, String status, String error, Instant updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reference", reference);
        m.put("display", display);
        m.put("hiType", hiType);
        m.put("status", status);
        m.put("error", error);
        m.put("updatedAt", AbdmFlows.iso(updatedAt));
        return m;
    }

    private static List<AbdmHipStore.CareContext> notLinked(List<AbdmHipStore.CareContext> rows) {
        return rows.stream().filter(c -> !LINKED.equals(c.status())).collect(Collectors.toList());
    }

    private static List<String> refs(List<AbdmHipStore.CareContext> rows) {
        return rows.stream().map(AbdmHipStore.CareContext::reference).collect(Collectors.toList());
    }
}
