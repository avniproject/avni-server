package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.domain.AbhaLink;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmSettings;
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

import static org.avni.server.service.abdm.hip.AbdmHipLinkingService.FAILED_FINAL;
import static org.avni.server.service.abdm.hip.AbdmHipLinkingService.HI_TYPE;
import static org.avni.server.service.abdm.hip.AbdmHipLinkingService.LINKED;
import static org.avni.server.service.abdm.hip.AbdmHipLinkingService.LINK_REQUESTED;
import static org.avni.server.service.abdm.hip.AbdmHipLinkingService.PENDING;

// M2: Avni as HIP through Eka Care's ABDM Connect (spec section 2). Linking: a subject's completed visits become care
// contexts, sent to Eka with the patient's Eka ID (recorded at M1) and no record, so Avni keeps custody (spec "Path B").
// Eka answers 202 and reports each care context later on the abha.link_care_context webhook. Eka relays an HIU's request
// on the abha.hip_data_fetch webhook and we answer on-fetch with the records encrypted to the requester's key.
// Care-context rows and statuses are in AbdmHipStore: PENDING -> LINK_REQUESTED -> LINKED | FAILED | FAILED_FINAL.
// HIP IDs are the organisation's (AbdmSettings): a visit is linked under its own (AbdmHipLinkingService.visitsByHip), and
// an event is acted on only for the HIP ID it names when that is the organisation's (AbdmFlows.hipOfEvent).
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

    private final AbdmSettings settings;
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
    private final IndividualRepository individualRepository;

    public EkaHipService(AbdmSettings settings, AbdmHipStore store, AbdmHipLinkingService linking, EkaAbdmClient eka, AbdmOrgContext orgContext,
                         AbhaEventRepository abhaEventRepository, EncounterRepository encounterRepository,
                         AbhaLinkRepository abhaLinkRepository, CareContextDocumentAssembler assembler, FhirBundleBuilder builder,
                         FideliusCrypto crypto, org.avni.server.service.abdm.hip.AbdmPatientPhones phones,
                         org.avni.server.dao.ProgramEncounterRepository programEncounterRepository,
                         IndividualRepository individualRepository) {
        this.programEncounterRepository = programEncounterRepository;
        this.individualRepository = individualRepository;
        this.settings = settings;
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

    // The patient as Eka knows them: the ABHA address and Eka ID of the ABHA on the saved record.
    private record EkaPatient(String abhaAddress, String oid) {
    }

    private EkaPatient ekaPatient(String subjectUuid, AbdmHipLinkingService.Patient patient, String abhaAddressSuffix) {
        // The Eka ID of the ABHA on the saved record, never of a later lookup of another ABHA left unsaved.
        AbhaEvent abha = orgContext.inReadOnlyTransaction(() -> AbhaEventService.ofSavedAbha(
                abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc(subjectUuid), patient.abhaNumber(), patient.abhaAddress())
                .orElse(null));
        if (abha == null) throw AbdmRequestException.conflict(NO_EKA_ID);
        String abhaAddress = AbdmFlows.withSuffix(AbdmFlows.firstNonNull(patient.abhaAddress(), abha.getAbhaAddress()), abhaAddressSuffix);
        if (abhaAddress == null) throw AbdmRequestException.conflict("This subject has no ABHA address");
        return new EkaPatient(abhaAddress, abha.getEkaOid());
    }

    public List<Map<String, Object>> linkCareContexts(String subjectUuid, boolean queuedOnly) {
        EkaSystemConfig orgSettings = settings.current();
        AbdmHipLinkingService.Patient patient = linking.loadPatient(subjectUuid);
        EkaPatient ekaPatient = ekaPatient(subjectUuid, patient, orgSettings.getAbhaAddressSuffix());
        String abhaAddress = ekaPatient.abhaAddress();
        String oid = ekaPatient.oid();

        // Avni starts the link: each visit goes under the HIP ID it is shared under.
        for (Map.Entry<String, List<AbdmHipLinkingService.Visit>> ofHip : AbdmHipLinkingService.visitsByHip(patient, orgSettings, store).entrySet()) {
            String hipId = ofHip.getKey();
            store.ensureCareContexts(patient.individualId(), hipId, patient.uuid(), HI_TYPE,
                    ofHip.getValue().stream().map(v -> new AbdmHipStore.NewCareContext(v.reference(), v.display())).toList());
            List<AbdmHipStore.CareContext> toLink = queuedOnly ? store.findCareContextsDue(patient.individualId(), hipId)
                    : store.findCareContexts(patient.individualId(), hipId).stream().filter(c -> !LINKED.equals(c.status())).toList();
            // One call per record type: a type Eka refuses fails only its own care contexts, never the patient's visits.
            Map<String, List<AbdmHipStore.CareContext>> byType = toLink.stream()
                    .collect(Collectors.groupingBy(AbdmHipStore.CareContext::hiType, LinkedHashMap::new, Collectors.toList()));
            for (List<AbdmHipStore.CareContext> ofType : byType.values()) {
                link(patient, hipId, abhaAddress, oid, ofType);
            }
        }
        return linking.careContexts(subjectUuid);
    }

    private void link(AbdmHipLinkingService.Patient patient, String hipId, String abhaAddress, String oid, List<AbdmHipStore.CareContext> toLink) {
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
            cc.put("hi_type", c.hiType());
        }

        // Persist before send: a webhook can arrive before post() returns.
        String linkRequestId = UUID.randomUUID().toString();
        store.markLinkRequested(hipId, references, linkRequestId);
        try {
            EkaResponse response = eka.post(LINK_PATH, body, patientHeaders(oid, patient.uuid(), hipId));
            logger.info("Eka link of {} care contexts for subject {}: HTTP {}", references.size(), patient.uuid(), response.status());
            applyAnswer(hipId, linkRequestId, response.body());
        } catch (RuntimeException e) {
            if (EkaAbdmClient.mayHaveActed(e)) {
                // Eka may have linked them, and its webhook may have said so already: they stay requested, a LINKED stays,
                // and the queue tries again once Eka has said nothing past the deadline.
                store.settleLinkRequest(linkRequestId, LINK_REQUESTED, "Eka gave no clear answer; it may have linked them: " + e.getMessage());
            } else if (EkaAbdmClient.refusedForGood(e)) {
                store.settleLinkRequest(linkRequestId, FAILED_FINAL, "Eka refused the link: " + e.getMessage());
            } else {
                store.failLinkRequest(linkRequestId, "Eka link failed: " + e.getMessage());
            }
            logger.warn("Eka link of care contexts for subject {} failed: {}", patient.uuid(), e.getMessage());
        }
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
                    case FAILED -> store.markStatus(hipId, List.of(reference), FAILED_FINAL, failure(status, c));
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
            case FAILED -> store.markByLinkRequest(linkRequestId, FAILED_FINAL, failure(status, body));
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
        Optional<String> hip = AbdmFlows.hipOfEvent(settings.current(), data);
        if (hip.isEmpty()) {
            logger.warn("Eka link status {} names a HIP ID that is not this organisation's; dropped", status);
            return;
        }
        String hipId = hip.get();
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
            rows.stream().map(AbdmHipStore.CareContext::individualId).distinct().forEach(individualId -> tellPatientLinked(individualId, hipId));
        } else {
            // Eka reports ERRORED after its own retries: final.
            store.markStatus(hipId, matched, FAILED_FINAL, failure(status, data));
        }
        logger.info("Eka link status {} applied to {} care contexts", status, matched.size());
    }

    // HIP_INIT_NOTIFY_HIECM: a text to the patient's phone saying a record was linked. NHA's call takes the phone number
    // and the HIP; Eka's (spec line 188) the number only. A refusal leaves the link as it is.
    private void tellPatientLinked(long individualId, String hipId) {
        Optional<String> phone = phones.phoneOf(individualId);
        if (phone.isEmpty()) {
            logger.info("Eka text message for subject {} not sent: Avni holds no phone number for the patient", individualId);
            return;
        }
        ObjectNode body = AbdmJson.obj();
        body.put("mobile_number", phone.get());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(EkaAbdmClient.H_HIP_ID, hipId);
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
        EkaSystemConfig orgSettings = settings.current();
        Optional<String> hip = AbdmFlows.hipOfEvent(orgSettings, data);
        if (hip.isEmpty()) {
            store.finishDataFlow(flowId, FLOW_FAILED, 0, "The request names a HIP ID that is not this organisation's");
            logger.warn("Eka data fetch {} names a HIP ID that is not this organisation's; nothing sent", transactionId);
            return;
        }
        String hipId = hip.get();

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
        String patientUuid = AbdmFlows.firstNonNull(AbdmJson.text(data, "partner_patient_id"), AbdmJson.text(data, "partner_user_id"));
        String oid = AbdmJson.text(data, "oid");
        String requesterAddress = AbdmJson.text(data, "abha_address");

        Map<String, AbdmHipStore.CareContext> linkedHere = store.findCareContextsByReferences(hipId, references).stream()
                .filter(c -> !PENDING.equals(c.status()))
                .collect(Collectors.toMap(AbdmHipStore.CareContext::reference, c -> c, (a, b) -> a, LinkedHashMap::new));
        // Share only what the consent covers. Each care context holds one record type: a visit is OPConsultation.
        // No hi_types means the request does not narrow by type.
        List<String> asked = AbdmFlows.texts(AbdmJson.at(data, "hi_types"), null);
        boolean anyType = asked == null || asked.isEmpty();
        if (!anyType && !linkedHere.isEmpty() && linkedHere.values().stream().noneMatch(c -> asked.contains(c.hiType()))) {
            store.finishDataFlow(flowId, FLOW_FAILED, 0, "Not sent: no record of the types asked for is held here; asked for " + String.join(", ", asked));
            logger.info("Eka data fetch {}: asked for record types not held here; nothing sent", transactionId);
            return;
        }
        List<String> problems = new ArrayList<>();
        ArrayNode entries = AbdmJson.arr();
        FideliusKeyMaterial ours;
        try {
            ours = crypto.generateKeyMaterial();
            for (String reference : references) {
                AbdmHipStore.CareContext row = linkedHere.get(reference);
                if (row == null) {
                    problems.add(reference + " (not linked through this HIP)");
                    continue;
                }
                if (!anyType && !asked.contains(row.hiType())) {
                    problems.add(reference + " (" + row.hiType() + ", not asked for)");
                    continue;
                }
                Prepared p = prepare(reference, patientUuid, oid, requesterAddress, hipId, orgSettings);
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
                entry.put("checksum", AbdmFlows.md5Hex(p.bundleJson())); // algorithm unverified
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
            eka.postSlow(ON_FETCH_PATH, body, patientHeaders(oid, patientUuid, hipId));
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

    private static final String NOT_THEIRS = "the ABHA asking is no longer the patient's";

    // Why the ABHA asking may not have the patient's records; null when it may. It must be the ABHA on the record, one Eka
    // verified (its record carries an Eka ID), asked by that Eka ID, its address, or the default address of its number.
    // When Eka names no ABHA there is nothing to check against.
    private String abhaProblem(AbhaLink link, String subjectUuid, String requesterOid, String requesterAddress) {
        if (!StringUtils.hasText(requesterOid) && !StringUtils.hasText(requesterAddress)) return null;
        if (link == null) return NOT_THEIRS;
        Optional<AbhaEvent> verified = AbhaEventService.ofSavedAbha(abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc(subjectUuid),
                link.getAbhaNumber(), link.getAbhaAddress());
        if (verified.isEmpty()) return "the patient's ABHA is not verified with Eka";
        if (StringUtils.hasText(requesterOid) && requesterOid.equals(verified.get().getEkaOid())) return null;
        if (!StringUtils.hasText(requesterAddress)) return NOT_THEIRS;
        String asking = requesterAddress.trim().split("@", 2)[0].toLowerCase(Locale.ROOT);
        String savedName = link.getAbhaAddress() == null ? null : link.getAbhaAddress().trim().split("@", 2)[0].toLowerCase(Locale.ROOT);
        String savedNumber = link.getAbhaNumber() == null ? "" : link.getAbhaNumber().replaceAll("[^0-9]", "");
        return asking.equals(savedName) || (!savedNumber.isEmpty() && asking.equals(savedNumber)) ? null : NOT_THEIRS;
    }

    // ---- building

    // Under the organisation's first HIP ID.
    Prepared prepare(String reference, String expectedPatientUuid) {
        EkaSystemConfig orgSettings = settings.current();
        return prepare(reference, expectedPatientUuid, null, null, orgSettings.hipIdOr(null), orgSettings);
    }

    // Read in one transaction: the encounter, its patient and their ABHA, as a FHIR bundle from the HIP hipId. requesterOid
    // and requesterAddress: the ABHA Eka asks for, which must still be the one on the patient's record.
    Prepared prepare(String reference, String expectedPatientUuid, String requesterOid, String requesterAddress, String hipId,
                     EkaSystemConfig orgSettings) {
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
                // none, or one Eka never verified, that ABHA gets nothing.
                String abhaProblem = abhaProblem(link, individual.getUuid(), requesterOid, requesterAddress);
                if (abhaProblem != null) return new Prepared(reference, null, abhaProblem);
                CareContextDocument document = assembler.assemble(encounter, individual,
                        link == null ? null : AbdmFlows.hyphenatedAbhaNumber(link.getAbhaNumber()),
                        link == null ? null : AbdmFlows.withSuffix(link.getAbhaAddress(), orgSettings.getAbhaAddressSuffix()),
                        hipId, orgSettings.getHipName(hipId));
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

    private static Map<String, String> patientHeaders(String oid, String partnerPatientId, String hipId) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(EkaAbdmClient.H_PATIENT_OID, oid);
        headers.put(EkaAbdmClient.H_PARTNER_PATIENT_ID, partnerPatientId);
        headers.put(EkaAbdmClient.H_HIP_ID, hipId);
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
        if (StringUtils.hasText(reason)) return AbdmFlows.maskIdentifiers(reason);
        String retries = AbdmJson.text(node, "retry_count");
        return "Eka reported " + status + (retries == null ? "" : " after " + retries + " retries");
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
