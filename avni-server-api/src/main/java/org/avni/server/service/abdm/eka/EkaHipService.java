package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.domain.AbhaLink;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmTime;
import org.avni.server.service.abdm.crypto.FideliusCrypto;
import org.avni.server.service.abdm.crypto.FideliusKeyMaterial;
import org.avni.server.service.abdm.fhir.CareContextDocument;
import org.avni.server.service.abdm.fhir.CareContextDocumentAssembler;
import org.avni.server.service.abdm.fhir.FhirBundleBuilder;
import org.avni.server.service.abdm.hip.AbdmFlows;
import org.avni.server.service.abdm.hip.AbdmHipLinkingService;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abha.AbhaEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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

import static org.avni.server.service.abdm.hip.AbdmHipLinkingService.FAILED;
import static org.avni.server.service.abdm.hip.AbdmHipLinkingService.HI_TYPE;
import static org.avni.server.service.abdm.hip.AbdmHipLinkingService.LINKED;
import static org.avni.server.service.abdm.hip.AbdmHipLinkingService.LINK_REQUESTED;
import static org.avni.server.service.abdm.hip.AbdmHipLinkingService.PENDING;

// DEMO-ONLY abdm-hackathon: never merged.
// M2: Avni as HIP through Eka Care's ABDM Connect (spec section 2). Linking: a subject's completed visits become care
// contexts, sent to Eka with the patient's Eka ID (recorded at M1) and no record, so Avni keeps custody (spec "Path B").
// Eka answers 202 and reports each care context later on the abha.link_care_context webhook. Eka relays an HIU's request
// on the abha.hip_data_fetch webhook and we answer on-fetch with the records encrypted to the requester's key.
// Care-context rows and statuses are in AbdmHipStore: PENDING -> LINK_REQUESTED -> LINKED | FAILED.
// Never logs an ABHA, an Eka ID, a key or a record.
@Service
public class EkaHipService implements EkaHipEvents {
    private static final Logger logger = LoggerFactory.getLogger(EkaHipService.class);

    public static final String LINK_PATH = "/abdm/v1/care-contexts/link";
    public static final String ON_FETCH_PATH = "/abdm/v1/hip/care-context/data/on-fetch";
    public static final String SMS_NOTIFY_PATH = "/abdm/v1/care-contexts/sms/notify";
    static final String MEDIA = "application/fhir+json";
    public static final String NO_EKA_ID = "Patient has no Eka ID: register or verify their ABHA through the app first";
    static final String FLOW_REQUESTED = "REQUESTED";
    static final String FLOW_TRANSFERRED = "TRANSFERRED";
    static final String FLOW_FAILED = "FAILED";
    // Eka did not answer an on-fetch it may still have taken: neither transferred nor failed.
    static final String FLOW_UNANSWERED = "UNANSWERED";

    // Eka documents LINKED and ERRORED; the rest are tolerated in case the sandbox says it differently.
    private static final Set<String> SAYS_LINKED = Set.of("LINKED", "SUCCESS", "SUCCESSFUL", "LINK_SUCCESS", "COMPLETED");
    private static final Set<String> SAYS_FAILED = Set.of("ERRORED", "ERROR", "FAILED", "FAILURE", "LINK_FAILED", "REJECTED");

    private final AbdmConfig cfg;
    private final AbdmHipStore store;
    private final AbdmHipLinkingService linking;
    private final EkaAbdmClient eka;
    private final AbdmOrgContext orgContext;
    private final AbhaEventRepository abhaEventRepository;
    private final EncounterRepository encounterRepository;
    private final AbhaLinkRepository abhaLinkRepository;
    private final CareContextDocumentAssembler assembler;
    private final FhirBundleBuilder builder;
    private final FideliusCrypto crypto;
    private final org.avni.server.service.abdm.hip.AbdmPatientPhones phones;
    private final org.avni.server.dao.ProgramEncounterRepository programEncounterRepository;

    public EkaHipService(AbdmConfig cfg, AbdmHipStore store, AbdmHipLinkingService linking, EkaAbdmClient eka, AbdmOrgContext orgContext,
                         AbhaEventRepository abhaEventRepository, EncounterRepository encounterRepository,
                         AbhaLinkRepository abhaLinkRepository, CareContextDocumentAssembler assembler, FhirBundleBuilder builder,
                         FideliusCrypto crypto, org.avni.server.service.abdm.hip.AbdmPatientPhones phones,
                         org.avni.server.dao.ProgramEncounterRepository programEncounterRepository) {
        this.programEncounterRepository = programEncounterRepository;
        this.cfg = cfg;
        this.store = store;
        this.linking = linking;
        this.eka = eka;
        this.orgContext = orgContext;
        this.abhaEventRepository = abhaEventRepository;
        this.encounterRepository = encounterRepository;
        this.abhaLinkRepository = abhaLinkRepository;
        this.assembler = assembler;
        this.builder = builder;
        this.crypto = crypto;
        this.phones = phones;
    }

    // One visit's FHIR bundle as JSON, or why there is none.
    record Prepared(String reference, String bundleJson, String problem) {
    }

    private enum Outcome {LINKED, FAILED, UNKNOWN}

    // ---- linking (the admin page's Link, and AbdmHousekeepingJob for visits queued on save)

    // Links every completed visit not yet linked; answers with the same view as AbdmHipLinkingService.careContexts.
    public List<Map<String, Object>> linkCareContexts(String subjectUuid) {
        return linkCareContexts(subjectUuid, false);
    }

    public List<Map<String, Object>> linkCareContexts(String subjectUuid, boolean queuedOnly) {
        AbdmHipLinkingService.Patient patient = linking.loadPatient(subjectUuid);
        // The Eka ID of the ABHA on the saved record, never of a later lookup of another ABHA left unsaved.
        AbhaEvent abha = orgContext.inReadOnlyTransaction(() -> AbhaEventService.ofSavedAbha(
                abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc(subjectUuid), patient.abhaNumber(), patient.abhaAddress())
                .orElse(null));
        if (abha == null) throw AbdmRequestException.conflict(NO_EKA_ID);
        String abhaAddress = AbdmFlows.withSuffix(AbdmFlows.firstNonNull(patient.abhaAddress(), abha.getAbhaAddress()), cfg.getAbhaAddressSuffix());
        if (abhaAddress == null) throw AbdmRequestException.conflict("This subject has no ABHA address");
        String oid = abha.getEkaOid();
        String hipId = cfg.getHipId();

        store.ensureCareContexts(patient.individualId(), hipId, patient.uuid(), HI_TYPE,
                patient.visits().stream().map(v -> new AbdmHipStore.NewCareContext(v.reference(), v.display())).toList());
        List<AbdmHipStore.CareContext> toLink = store.findCareContexts(patient.individualId(), hipId).stream()
                .filter(c -> !LINKED.equals(c.status()))
                .filter(c -> !queuedOnly || AbdmHipLinkingService.QUEUED.equals(c.status()) || AbdmHipLinkingService.FAILED.equals(c.status()))
                .toList();
        if (toLink.isEmpty()) return linking.careContexts(subjectUuid);
        List<String> references = toLink.stream().map(AbdmHipStore.CareContext::reference).toList();

        ObjectNode body = AbdmJson.obj();
        body.put("abha_address", abhaAddress);
        body.put("oid", oid);
        body.put("partner_user_id", patient.uuid());
        ArrayNode list = body.putArray("care_contexts");
        for (AbdmHipStore.CareContext c : toLink) {
            ObjectNode cc = list.addObject();
            cc.put("care_context_id", c.reference());
            cc.put("display", c.display());
            cc.put("hi_type", HI_TYPE);
        }

        // Persist before send: a webhook can arrive before post() returns.
        String linkRequestId = UUID.randomUUID().toString();
        store.markLinkRequested(hipId, references, linkRequestId);
        try {
            EkaResponse response = eka.post(LINK_PATH, body, patientHeaders(oid, patient.uuid()));
            logger.info("Eka link of {} care contexts for subject {}: HTTP {}", references.size(), patient.uuid(), response.status());
            applyAnswer(hipId, linkRequestId, response.body());
        } catch (RuntimeException e) {
            if (EkaAbdmClient.mayHaveActed(e)) {
                // Eka may have linked them, and its webhook may have said so already: they stay requested, a LINKED stays.
                store.settleLinkRequest(linkRequestId, LINK_REQUESTED, "Eka gave no clear answer; it may have linked them: " + e.getMessage());
            } else {
                store.settleLinkRequest(linkRequestId, FAILED, "Eka link failed: " + e.getMessage());
            }
            logger.warn("Eka link of care contexts for subject {} failed: {}", patient.uuid(), e.getMessage());
        }
        return linking.careContexts(subjectUuid);
    }

    // Eka documents 202 with no body. A body that carries a status, overall or per care context, is applied.
    private void applyAnswer(String hipId, String linkRequestId, JsonNode body) {
        if (body == null || !body.isObject()) return;
        JsonNode perCareContext = firstArray(body, "care_contexts", "data.care_contexts");
        if (perCareContext != null) {
            List<String> linked = new ArrayList<>();
            for (JsonNode c : perCareContext) {
                String reference = AbdmFlows.firstNonNull(AbdmJson.text(c, "care_context_id"), AbdmJson.text(c, "reference"), AbdmJson.text(c, "id"));
                String status = AbdmJson.text(c, "status");
                if (reference == null) continue;
                switch (outcome(status)) {
                    case LINKED -> linked.add(reference);
                    case FAILED -> store.markStatus(hipId, List.of(reference), FAILED, failure(status, c));
                    default -> {
                    }
                }
            }
            if (!linked.isEmpty()) store.markLinked(hipId, linked, "HIP");
            return;
        }
        String status = AbdmFlows.firstNonNull(AbdmJson.text(body, "status"), AbdmJson.text(body, "data.status"));
        switch (outcome(status)) {
            case LINKED -> store.markByLinkRequest(linkRequestId, LINKED, null);
            case FAILED -> store.markByLinkRequest(linkRequestId, FAILED, failure(status, body));
            default -> {
            }
        }
    }

    // ---- webhooks

    // abha.link_care_context: one care context's outcome. Matched by care_context_id, else the patient's requested visits.
    @Override
    public void linkCareContext(JsonNode event) {
        JsonNode data = dataOf(event);
        String status = AbdmJson.text(data, "status");
        Outcome outcome = outcome(status);
        if (outcome == Outcome.UNKNOWN) {
            logger.info("Eka link status '{}' not understood; nothing changed", status);
            return;
        }
        String hipId = cfg.getHipId();
        List<String> references = careContextIds(data);
        List<AbdmHipStore.CareContext> rows = references.isEmpty() ? new ArrayList<>() : store.findCareContextsByReferences(hipId, references);
        if (rows.isEmpty()) {
            String patientUuid = AbdmFlows.firstNonNull(AbdmJson.text(data, "partner_patient_id"), AbdmJson.text(data, "partner_user_id"));
            if (patientUuid != null) {
                rows = store.findSubjectByUuid(patientUuid)
                        .map(s -> store.findCareContexts(s.id(), hipId).stream().filter(c -> LINK_REQUESTED.equals(c.status()))
                                .collect(Collectors.toList()))
                        .orElse(new ArrayList<>());
            }
        }
        List<String> matched = rows.stream().map(AbdmHipStore.CareContext::reference).collect(Collectors.toList());
        if (matched.isEmpty()) {
            logger.warn("Eka link status {} names no care context of ours; dropped", status);
            return;
        }
        if (outcome == Outcome.LINKED) {
            store.markLinked(hipId, matched, "HIP");
            rows.stream().map(AbdmHipStore.CareContext::individualId).distinct().forEach(this::tellPatientLinked);
        } else {
            store.markStatus(hipId, matched, FAILED, failure(status, data));
        }
        logger.info("Eka link status {} applied to {} care contexts", status, matched.size());
    }

    // HIP_INIT_NOTIFY_HIECM: a text to the patient's phone saying a record was linked. NHA's call takes the phone number
    // and the HIP; Eka's (spec line 188) the number only. A refusal leaves the link as it is.
    private void tellPatientLinked(long individualId) {
        Optional<String> phone = phones.phoneOf(individualId);
        if (phone.isEmpty()) {
            logger.info("Eka text message for subject {} not sent: Avni holds no phone number for the patient", individualId);
            return;
        }
        ObjectNode body = AbdmJson.obj();
        body.put("mobile_number", phone.get());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(EkaAbdmClient.H_HIP_ID, cfg.getHipId());
        try {
            eka.post(SMS_NOTIFY_PATH, body, headers);
            logger.info("Eka text message sent to the patient of subject {} for a linked record", individualId);
        } catch (RuntimeException e) {
            logger.warn("Eka text message for subject {} not sent: {}", individualId, e.getMessage());
        }
    }

    // abha.hip_data_fetch: build each requested visit's bundle, encrypt it to the requester's key with a
    // fresh key pair of ours, and post it on-fetch. Recorded as an HIP data flow.
    @Override
    public void hipDataFetch(JsonNode event) {
        JsonNode data = dataOf(event);
        String eventId = AbdmJson.text(event, "transaction_id");
        // Spec 2.4: data.transaction_id is the one to echo; the top-level one only when it is empty. Unverified.
        String transactionId = AbdmFlows.firstNonNull(AbdmJson.text(data, "transaction_id"), eventId);
        String consentId = AbdmFlows.firstNonNull(AbdmJson.text(data, "consent_id"), AbdmJson.text(data, "consent_artefact_id"),
                "eka:" + transactionId);
        long flowId = store.insertDataFlow(cut(consentId, 100), transactionId, eventId, "eka:" + ON_FETCH_PATH, FLOW_REQUESTED, null);

        String requesterKey = AbdmFlows.firstNonNull(AbdmJson.text(data, "key_information.dh_public_key.key_value"),
                AbdmJson.text(data, "key_material.dh_public_key.key_value"), AbdmJson.text(data, "keyMaterial.dhPublicKey.keyValue"));
        String requesterNonce = AbdmFlows.firstNonNull(AbdmJson.text(data, "key_information.nonce"),
                AbdmJson.text(data, "key_material.nonce"), AbdmJson.text(data, "keyMaterial.nonce"));
        if (requesterKey == null || requesterNonce == null) {
            store.finishDataFlow(flowId, FLOW_FAILED, 0, "The request carries no key_information (requester key and nonce)");
            logger.warn("Eka data fetch {} has no key material; nothing sent", transactionId);
            return;
        }
        List<String> references = careContextIds(data);
        if (references.isEmpty()) {
            store.finishDataFlow(flowId, FLOW_FAILED, 0, "The request names no care context");
            return;
        }
        // Share only what the consent covers: the visits held here are OPConsultation records.
        List<String> asked = AbdmFlows.texts(AbdmJson.at(data, "hi_types"), null);
        if (asked != null && !asked.isEmpty() && !asked.contains(HI_TYPE)) {
            store.finishDataFlow(flowId, FLOW_FAILED, 0, "Not sent: only " + HI_TYPE + " records are held here; asked for " + String.join(", ", asked));
            logger.info("Eka data fetch {}: asked for record types not held here; nothing sent", transactionId);
            return;
        }
        String patientUuid = AbdmFlows.firstNonNull(AbdmJson.text(data, "partner_patient_id"), AbdmJson.text(data, "partner_user_id"));
        String oid = AbdmJson.text(data, "oid");
        String requesterAddress = AbdmJson.text(data, "abha_address");
        String hipId = cfg.getHipId();

        Set<String> linkedHere = store.findCareContextsByReferences(hipId, references).stream()
                .filter(c -> !PENDING.equals(c.status())).map(AbdmHipStore.CareContext::reference).collect(Collectors.toSet());
        List<String> problems = new ArrayList<>();
        ArrayNode entries = AbdmJson.arr();
        FideliusKeyMaterial ours;
        try {
            ours = crypto.generateKeyMaterial();
            for (String reference : references) {
                if (!linkedHere.contains(reference)) {
                    problems.add(reference + " (not linked through this HIP)");
                    continue;
                }
                Prepared p = prepare(reference, patientUuid, oid, requesterAddress);
                if (p.bundleJson() == null) {
                    problems.add(reference + " (" + p.problem() + ")");
                    continue;
                }
                String content;
                try {
                    content = crypto.encrypt(p.bundleJson(), ours.privateKeyBase64(), ours.nonceBase64(), requesterKey, requesterNonce);
                } catch (RuntimeException e) {
                    problems.add(reference + " (could not be encrypted to the requester's key)");
                    continue;
                }
                ObjectNode entry = entries.addObject();
                entry.put("care_context_id", reference);
                entry.put("media", MEDIA);
                entry.put("checksum", AbdmFlows.md5Hex(p.bundleJson())); // DEMO-ONLY: algorithm unverified
                entry.put("content", content);
            }
        } catch (RuntimeException e) {
            store.finishDataFlow(flowId, FLOW_FAILED, 0, "Transfer failed: " + e.getClass().getSimpleName());
            logger.error("Eka data fetch {} failed: {}", transactionId, e.getClass().getSimpleName());
            return;
        }
        String notSent = problems.isEmpty() ? null : "Not sent: " + String.join(", ", problems);
        if (entries.isEmpty()) {
            store.finishDataFlow(flowId, FLOW_FAILED, 0, notSent == null ? "No record could be sent" : notSent);
            logger.warn("Eka data fetch {}: no record could be sent", transactionId);
            return;
        }

        ObjectNode body = AbdmJson.obj();
        body.put("transaction_id", transactionId);
        body.put("page_number", 1);
        body.put("page_count", 1);
        body.set("entries", entries);
        body.set("key_information", keyInformation(ours));
        try {
            // Eka has taken over 10 s to answer an on-fetch (26 Oct: 3 of 18), so the share gets the slow limit.
            eka.postSlow(ON_FETCH_PATH, body, patientHeaders(oid, patientUuid));
            store.finishDataFlow(flowId, FLOW_TRANSFERRED, entries.size(), notSent);
            logger.info("Eka data fetch {}: {} records sent on-fetch", transactionId, entries.size());
        } catch (RuntimeException e) {
            if (!EkaAbdmClient.mayHaveActed(e)) {
                store.finishDataFlow(flowId, FLOW_FAILED, 0, "On-fetch failed: " + e.getMessage());
                logger.warn("Eka on-fetch for {} failed: {}", transactionId, e.getMessage());
                return;
            }
            String unanswered = "Eka may have the records: " + e.getMessage() + (notSent == null ? "" : ". " + notSent);
            store.finishDataFlow(flowId, FLOW_UNANSWERED, entries.size(), unanswered);
            logger.warn("Eka on-fetch for {} not answered: {}", transactionId, e.getMessage());
        }
    }

    // The ABHA asking is the one on the record: its Eka ID, its address, or the default address of its number. When
    // Eka names no ABHA there is nothing to check against.
    private boolean stillTheirAbha(AbhaLink link, String subjectUuid, String requesterOid, String requesterAddress) {
        if (!StringUtils.hasText(requesterOid) && !StringUtils.hasText(requesterAddress)) return true;
        if (link == null) return false;
        if (StringUtils.hasText(requesterOid) && AbhaEventService.ofSavedAbha(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc(subjectUuid),
                link.getAbhaNumber(), link.getAbhaAddress()).map(AbhaEvent::getEkaOid).filter(requesterOid::equals).isPresent()) return true;
        if (!StringUtils.hasText(requesterAddress)) return false;
        String asking = requesterAddress.trim().split("@", 2)[0].toLowerCase(Locale.ROOT);
        String savedName = link.getAbhaAddress() == null ? null : link.getAbhaAddress().trim().split("@", 2)[0].toLowerCase(Locale.ROOT);
        String savedNumber = link.getAbhaNumber() == null ? "" : link.getAbhaNumber().replaceAll("[^0-9]", "");
        return asking.equals(savedName) || (!savedNumber.isEmpty() && asking.equals(savedNumber));
    }

    // ---- building

    Prepared prepare(String reference, String expectedPatientUuid) {
        return prepare(reference, expectedPatientUuid, null, null);
    }

    // Read in one transaction: the encounter, its patient and their ABHA, as a FHIR bundle. requesterOid and
    // requesterAddress: the ABHA Eka asks for, which must still be the one on the patient's record.
    Prepared prepare(String reference, String expectedPatientUuid, String requesterOid, String requesterAddress) {
        try {
            return orgContext.inReadOnlyTransaction(() -> {
                org.avni.server.domain.AbstractEncounter encounter = encounterRepository.findByUuid(reference);
                Individual individual = encounter == null ? null : ((Encounter) encounter).getIndividual();
                if (encounter == null) {
                    org.avni.server.domain.ProgramEncounter programmeVisit = programEncounterRepository.findByUuid(reference);
                    encounter = programmeVisit;
                    individual = programmeVisit == null || programmeVisit.getProgramEnrolment() == null ? null
                            : programmeVisit.getProgramEnrolment().getIndividual();
                }
                if (encounter == null || encounter.isVoided() || encounter.getEncounterDateTime() == null) {
                    return new Prepared(reference, null, "visit not found");
                }
                // Voiding a patient leaves their visits as they are, and a visit already linked stays linked at NHA.
                if (individual == null || individual.isVoided()) {
                    return new Prepared(reference, null, "patient removed");
                }
                if (expectedPatientUuid != null && (individual == null || !expectedPatientUuid.equals(individual.getUuid()))) {
                    return new Prepared(reference, null, "belongs to another patient");
                }
                AbhaLink link = abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(individual.getId()).orElse(null);
                // A visit stays linked at NHA under the ABHA it was linked with; once the record holds another ABHA, or
                // none, that ABHA gets nothing.
                if (!stillTheirAbha(link, individual.getUuid(), requesterOid, requesterAddress)) {
                    return new Prepared(reference, null, "the ABHA asking is no longer the patient's");
                }
                CareContextDocument document = assembler.assemble(encounter, individual,
                        link == null ? null : AbdmFlows.hyphenatedAbhaNumber(link.getAbhaNumber()),
                        link == null ? null : AbdmFlows.withSuffix(link.getAbhaAddress(), cfg.getAbhaAddressSuffix()),
                        cfg.getHipId(), cfg.getHipName());
                return new Prepared(reference, AbdmJson.write(builder.build(document)), null);
            });
        } catch (RuntimeException e) {
            logger.warn("Eka: care context {} not prepared: {}", reference, e.getClass().getSimpleName());
            return new Prepared(reference, null, "could not be prepared");
        }
    }

    // Our key material for the requester to derive the shared key: X.509 public key, as the Eka library shares it.
    private static ObjectNode keyInformation(FideliusKeyMaterial ours) {
        ObjectNode km = AbdmJson.obj();
        km.put("crypto_alg", AbdmFlows.CRYPTO_ALG);
        km.put("curve", AbdmFlows.CURVE);
        ObjectNode dh = km.putObject("dh_public_key");
        dh.put("expiry", AbdmTime.format(Instant.now().plus(Duration.ofHours(1))));
        dh.put("parameters", AbdmFlows.KEY_PARAMETERS);
        dh.put("key_value", ours.publicKeyX509Base64());
        km.put("nonce", ours.nonceBase64());
        return km;
    }

    private Map<String, String> patientHeaders(String oid, String partnerPatientId) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(EkaAbdmClient.H_PATIENT_OID, oid);
        headers.put(EkaAbdmClient.H_PARTNER_PATIENT_ID, partnerPatientId);
        headers.put(EkaAbdmClient.H_HIP_ID, cfg.getHipId());
        return headers;
    }

    // ---- reading events

    private static JsonNode dataOf(JsonNode event) {
        JsonNode data = event == null ? null : event.get("data");
        return data != null && data.isObject() ? data : event;
    }

    // care_context_id (one), or care_contexts as strings or objects.
    private static List<String> careContextIds(JsonNode data) {
        List<String> ids = new ArrayList<>();
        String one = AbdmFlows.firstNonNull(AbdmJson.text(data, "care_context_id"), AbdmJson.text(data, "care_context_reference"),
                AbdmJson.text(data, "careContextId"));
        if (one != null) ids.add(one);
        JsonNode many = firstArray(data, "care_contexts", "careContexts");
        if (many != null) {
            for (JsonNode c : many) {
                String id = c.isValueNode() ? c.asText() : AbdmFlows.firstNonNull(AbdmJson.text(c, "care_context_id"),
                        AbdmJson.text(c, "id"), AbdmJson.text(c, "reference"), AbdmJson.text(c, "careContextReference"));
                if (StringUtils.hasText(id) && !ids.contains(id)) ids.add(id);
            }
        }
        return ids;
    }

    private static JsonNode firstArray(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode n = AbdmJson.at(node, path);
            if (n != null && n.isArray()) return n;
        }
        return null;
    }

    private static Outcome outcome(String status) {
        if (status == null) return Outcome.UNKNOWN;
        String s = status.trim().toUpperCase(Locale.ROOT);
        if (SAYS_LINKED.contains(s)) return Outcome.LINKED;
        if (SAYS_FAILED.contains(s)) return Outcome.FAILED;
        return Outcome.UNKNOWN;
    }

    // Eka's error, a string or an object (its type is undocumented), else the status itself.
    private static String failure(String status, JsonNode node) {
        JsonNode error = node == null ? null : node.get("error");
        String reason = null;
        if (error != null && !error.isNull()) {
            if (error.isObject()) {
                String code = AbdmJson.text(error, "code");
                String message = AbdmFlows.firstNonNull(AbdmJson.text(error, "message"), AbdmJson.text(error, "error"));
                reason = code == null ? message : (message == null ? code : code + ": " + message);
                if (reason == null) reason = AbdmJson.write(error);
            } else {
                reason = error.asText();
            }
        }
        if (StringUtils.hasText(reason)) return reason;
        String retries = AbdmJson.text(node, "retry_count");
        return "Eka reported " + status + (retries == null ? "" : " after " + retries + " retries");
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
