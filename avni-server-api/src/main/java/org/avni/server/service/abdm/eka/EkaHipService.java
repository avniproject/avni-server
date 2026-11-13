package org.avni.server.service.abdm.eka;

import com.bugsnag.Bugsnag;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.avni.server.service.abha.AbhaVerifiedRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.StringUtils;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
// Care-context rows and statuses are in AbdmHipStore: PENDING -> LINK_REQUESTED -> LINKED | FAILED | FAILED_FINAL, and
// AWAITING_VERIFIED_ABHA while the patient's ABHA is not verified with Eka.
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
    public static final String NO_EKA_ID = AbdmHipLinkingService.NO_EKA_ID;
    static final String FLOW_REQUESTED = "REQUESTED";
    static final String FLOW_TRANSFERRED = "TRANSFERRED";
    static final String FLOW_FAILED = "FAILED";
    // Eka did not answer an on-fetch it may still have taken: neither transferred nor failed.
    static final String FLOW_UNANSWERED = "UNANSWERED";
    // This try failed in a way that may pass; the inbox tries the share again, under a flow of its own.
    static final String FLOW_RETRIED = "RETRIED";

    // Eka documents LINKED and ERRORED; the rest are tolerated in case the sandbox says it differently.
    private static final Set<String> SAYS_LINKED = Set.of("LINKED", "SUCCESS", "SUCCESSFUL", "LINK_SUCCESS", "COMPLETED");
    private static final Set<String> SAYS_FAILED = Set.of("ERRORED", "ERROR", "FAILED", "FAILURE", "LINK_FAILED", "REJECTED");

    private final AbdmSettings settings;
    private final AbdmHipStore store;
    private final AbdmHipLinkingService linking;
    private final EkaAbdmClient eka;
    private final AbdmOrgContext orgContext;
    private final AbhaVerifiedRecords verifiedRecords;
    private final EncounterRepository encounterRepository;
    private final AbhaLinkRepository abhaLinkRepository;
    private final CareContextDocumentAssembler assembler;
    private final FhirBundleBuilder builder;
    private final FideliusCrypto crypto;
    private final org.avni.server.service.abdm.hip.AbdmPatientPhones phones;
    private final org.avni.server.dao.ProgramEncounterRepository programEncounterRepository;
    private final IndividualRepository individualRepository;
    private final Bugsnag bugsnag;

    public EkaHipService(AbdmSettings settings, AbdmHipStore store, AbdmHipLinkingService linking, EkaAbdmClient eka, AbdmOrgContext orgContext,
                         AbhaVerifiedRecords verifiedRecords, EncounterRepository encounterRepository,
                         AbhaLinkRepository abhaLinkRepository, CareContextDocumentAssembler assembler, FhirBundleBuilder builder,
                         FideliusCrypto crypto, org.avni.server.service.abdm.hip.AbdmPatientPhones phones,
                         org.avni.server.dao.ProgramEncounterRepository programEncounterRepository,
                         IndividualRepository individualRepository, Bugsnag bugsnag) {
        this.bugsnag = bugsnag;
        this.programEncounterRepository = programEncounterRepository;
        this.individualRepository = individualRepository;
        this.settings = settings;
        this.store = store;
        this.linking = linking;
        this.eka = eka;
        this.orgContext = orgContext;
        this.verifiedRecords = verifiedRecords;
        this.encounterRepository = encounterRepository;
        this.abhaLinkRepository = abhaLinkRepository;
        this.assembler = assembler;
        this.builder = builder;
        this.crypto = crypto;
        this.phones = phones;
    }

    // No Eka-verified ABHA on the patient's record yet: the queued visits wait for one instead of being given up.
    public static final class NoVerifiedAbha extends AbdmRequestException {
        public NoVerifiedAbha() {
            this(NO_EKA_ID);
        }

        // Why they wait, kept on the waiting visits (AbdmHipStore.awaitVerifiedAbha).
        public NoVerifiedAbha(String reason) {
            super(org.springframework.http.HttpStatus.CONFLICT, reason);
        }
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
        AbhaVerifiedRecords.Verification verification = orgContext.inReadOnlyTransaction(
                () -> verifiedRecords.check(subjectUuid, patient.abhaNumber(), patient.abhaAddress()));
        if (verification.heldByAnother()) throw new NoVerifiedAbha(AbhaVerifiedRecords.HELD_BY_ANOTHER);
        AbhaEvent abha = verification.record();
        if (abha == null) throw new NoVerifiedAbha();
        // The verified ABHA's own address: the record's ABHA answers are typed, and a typed one may be someone else's.
        String abhaAddress = verifiedAddress(abha, abhaAddressSuffix);
        if (abhaAddress == null) throw AbdmRequestException.conflict("This subject's verified ABHA has neither an address nor a number");
        return new EkaPatient(abhaAddress, abha.getEkaOid());
    }

    public List<Map<String, Object>> linkCareContexts(String subjectUuid, boolean queuedOnly) {
        EkaSystemConfig orgSettings = settings.current();
        AbdmHipLinkingService.Patient patient = linking.loadPatient(subjectUuid);
        EkaPatient ekaPatient = ekaPatient(subjectUuid, patient, orgSettings.getAbhaAddressSuffix());
        String abhaAddress = ekaPatient.abhaAddress();
        String oid = ekaPatient.oid();

        // Avni starts the link: each visit goes under the HIP ID it is shared under. Every HIP ID is looked at, as one with no
        // current visit left may still hold visits removed since they were queued.
        Map<String, List<AbdmHipLinkingService.Visit>> byHip = AbdmHipLinkingService.visitsByHip(patient, orgSettings, store);
        Set<String> current = patient.visits().stream().map(AbdmHipLinkingService.Visit::reference).collect(Collectors.toSet());
        Set<String> hipIds = new LinkedHashSet<>(byHip.keySet());
        hipIds.addAll(orgSettings.getHipIds());
        for (String hipId : hipIds) {
            List<AbdmHipLinkingService.Visit> visits = byHip.getOrDefault(hipId, List.of());
            if (!visits.isEmpty()) {
                store.ensureCareContexts(patient.individualId(), hipId, patient.uuid(), HI_TYPE,
                        visits.stream().map(v -> new AbdmHipStore.NewCareContext(v.reference(), v.display())).toList());
            }
            List<AbdmHipStore.CareContext> waiting = queuedOnly ? store.findCareContextsDue(patient.individualId(), hipId)
                    : store.findCareContexts(patient.individualId(), hipId).stream().filter(c -> !LINKED.equals(c.status())).toList();
            // A visit voided or cancelled since its care context was made is no longer the patient's to share.
            List<String> removed = waiting.stream().filter(c -> HI_TYPE.equals(c.hiType()) && !current.contains(c.reference()))
                    .map(AbdmHipStore.CareContext::reference).toList();
            if (!removed.isEmpty()) {
                store.giveUpRemovedVisits(List.of(hipId), removed);
                logger.info("ABDM: {} visit(s) of subject {} given up under a HIP ID: no longer the patient's", removed.size(), patient.uuid());
            }
            if (visits.isEmpty()) continue;
            List<AbdmHipStore.CareContext> toLink = waiting.stream().filter(c -> !removed.contains(c.reference())).toList();
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
        } catch (EkaCallException e) {
            if (EkaAbdmClient.refusedForGood(e)) {
                if (EkaAbdmClient.mayHaveActed(e)) {
                    // An earlier try of this call may have linked them: they stay requested, and a LINKED stays.
                    store.settleLinkRequest(linkRequestId, LINK_REQUESTED, "Eka gave no clear answer; it may have linked them: " + e.getMessage());
                } else {
                    store.settleLinkRequest(linkRequestId, FAILED_FINAL, "Eka refused the link: " + e.getMessage());
                }
                logger.warn("Eka link of care contexts for subject {} failed: {}", patient.uuid(), e.getMessage());
                return;
            }
            // Eka failed as it would for any patient (its login, 401, 403, a server error, no answer): no try is counted
            // against these visits, and the failure is thrown, so the run stops before every patient's tries go the same way.
            if (EkaAbdmClient.mayHaveActed(e)) {
                store.awaitLinkAnswer(linkRequestId, "Eka gave no clear answer; it may have linked them: " + e.getMessage());
            } else {
                store.putBackLinkRequest(linkRequestId, "Eka did not take the link: " + e.getMessage());
            }
            logger.warn("Eka link of care contexts for subject {} not made, Eka failing for every patient: {}", patient.uuid(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            // A fault of Avni's own may pass on a later try: counted, and tried again after a wait.
            store.failLinkRequest(linkRequestId, "Eka link failed: " + e.getMessage());
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
            // Taken as final on the unverified belief that Eka reports ERRORED only after its own retries.
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
            // Each try reaches the patient's phone, so it is never sent again after a server error.
            eka.postAtMostOnce(SMS_NOTIFY_PATH, body, headers);
            logger.info("Eka text message sent to the patient of subject {} for a linked record", individualId);
        } catch (RuntimeException e) {
            logger.warn("Eka text message for subject {} not sent: {}", individualId, e.getMessage());
        }
    }

    // abha.hip_data_fetch: build each requested visit's bundle, encrypt it to the requester's key with a
    // fresh key pair of ours, and post it on-fetch. Recorded as an HIP data flow. A fault that may pass is thrown for the
    // inbox to try again, until its last try; once Eka may have the records nothing is thrown, so they are never sent twice.
    @Override
    public void hipDataFetch(JsonNode event, boolean lastTry) {
        JsonNode data = dataOf(event);
        String eventId = AbdmJson.text(event, "transaction_id");
        // Spec 2.4: data.transaction_id is the one to echo; the top-level one only when it is empty. Unverified.
        String transactionId = AbdmFlows.firstNonNull(AbdmJson.text(data, "transaction_id"), eventId);
        String consentId = AbdmFlows.firstNonNull(AbdmJson.text(data, "consent_id"), AbdmJson.text(data, "consent_artefact_id"),
                "eka:" + transactionId);
        // The inbox may hand over again a share it could not mark done; one sent, or maybe sent, is never posted twice.
        if (store.shareSent(transactionId)) {
            logger.info("Eka data fetch {} was sent before; not sent again", transactionId);
            return;
        }
        long flowId = store.insertDataFlow(cut(consentId, 100), transactionId, eventId, "eka:" + ON_FETCH_PATH, FLOW_REQUESTED, null);
        Request request;
        try {
            request = request(data, flowId, transactionId);
        } catch (RuntimeException e) {
            String error = "Request not read: " + e.getClass().getSimpleName();
            if (!lastTry) throw retried(flowId, transactionId, error, e);
            failShare(flowId, transactionId, error, e);
            return;
        }
        if (request == null) return;

        List<String> problems = new ArrayList<>();
        ArrayNode entries = AbdmJson.arr();
        FideliusKeyMaterial ours;
        try {
            ours = crypto.generateKeyMaterial();
            for (String reference : request.references()) {
                AbdmHipStore.CareContext row = request.linkedHere().get(reference);
                if (row == null) {
                    problems.add(reference + " (not linked through this HIP)");
                    continue;
                }
                if (!request.anyType() && !request.asked().contains(row.hiType())) {
                    problems.add(reference + " (" + row.hiType() + ", not asked for)");
                    continue;
                }
                Prepared p;
                try {
                    p = prepare(reference, request.patientUuid(), request.oid(), request.requesterAddress(), request.hipId(), request.orgSettings());
                } catch (RuntimeException e) {
                    // Only a passing database fault holds the share back; any other would fail on every try.
                    if (!lastTry && transientDatabaseFault(e)) throw e;
                    logger.warn("Eka: care context {} not prepared: {}", reference, e.getClass().getSimpleName());
                    bugsnag.notify(e);
                    p = new Prepared(reference, null, "could not be prepared");
                }
                if (p.bundleJson() == null) {
                    problems.add(reference + " (" + p.problem() + ")");
                    continue;
                }
                String content;
                try {
                    content = crypto.encrypt(p.bundleJson(), ours.privateKeyBase64(), ours.nonceBase64(), request.requesterKey(), request.requesterNonce());
                } catch (RuntimeException e) {
                    bugsnag.notify(e);
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
            String error = "Transfer failed: " + e.getClass().getSimpleName();
            if (!lastTry) throw retried(flowId, transactionId, error, e);
            failShare(flowId, transactionId, error, e);
            return;
        }
        String notSent = problems.isEmpty() ? null : "Not sent: " + String.join(", ", problems);
        if (entries.isEmpty()) {
            // A visit's fault was reported as it happened; what is left is a refusal.
            refuseShare(flowId, transactionId, notSent == null ? "No record could be sent" : notSent);
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
            eka.postSlow(ON_FETCH_PATH, body, patientHeaders(request.oid(), request.patientUuid(), request.hipId()));
        } catch (RuntimeException e) {
            if (EkaAbdmClient.mayHaveActed(e)) {
                String unanswered = "Eka may have the records: " + e.getMessage() + (notSent == null ? "" : ". " + notSent);
                noteSent(flowId, transactionId, FLOW_UNANSWERED, entries.size(), unanswered);
                logger.warn("Eka on-fetch for {} not answered: {}", transactionId, e.getMessage());
                return;
            }
            String error = "On-fetch failed: " + e.getMessage();
            if (!lastTry && !EkaAbdmClient.refusedForGood(e)) throw retried(flowId, transactionId, error, e);
            failShare(flowId, transactionId, error, e);
            return;
        }
        noteSent(flowId, transactionId, FLOW_TRANSFERRED, entries.size(), notSent);
        logger.info("Eka data fetch {}: {} records sent on-fetch", transactionId, entries.size());
    }

    // What a data fetch asks for, once read and checked against what is held here.
    private record Request(EkaSystemConfig orgSettings, String hipId, String requesterKey, String requesterNonce, List<String> references,
                           String patientUuid, String oid, String requesterAddress, Map<String, AbdmHipStore.CareContext> linkedHere,
                           List<String> asked) {
        boolean anyType() {
            return asked == null || asked.isEmpty();
        }
    }

    // The request, or null when it cannot be served: refused, or not readable as Eka's, recorded FAILED either way.
    private Request request(JsonNode data, long flowId, String transactionId) {
        EkaSystemConfig orgSettings = settings.current();
        Optional<String> hip = AbdmFlows.hipOfEvent(orgSettings, data);
        if (hip.isEmpty()) {
            refuseShare(flowId, transactionId, "The request names a HIP ID that is not this organisation's");
            return null;
        }
        String hipId = hip.get();

        String requesterKey = AbdmFlows.firstNonNull(AbdmJson.text(data, "key_information.dh_public_key.key_value"),
                AbdmJson.text(data, "key_material.dh_public_key.key_value"), AbdmJson.text(data, "keyMaterial.dhPublicKey.keyValue"));
        String requesterNonce = AbdmFlows.firstNonNull(AbdmJson.text(data, "key_information.nonce"),
                AbdmJson.text(data, "key_material.nonce"), AbdmJson.text(data, "keyMaterial.nonce"));
        if (requesterKey == null || requesterNonce == null) {
            failShare(flowId, transactionId, "The request carries no key_information (requester key and nonce)", null);
            return null;
        }
        List<String> references = careContextIds(data);
        if (references.isEmpty()) {
            failShare(flowId, transactionId, "The request names no care context", null);
            return null;
        }
        String patientUuid = AbdmFlows.firstNonNull(AbdmJson.text(data, "partner_patient_id"), AbdmJson.text(data, "partner_user_id"));
        String oid = AbdmJson.text(data, "oid");
        String requesterAddress = AbdmJson.text(data, "abha_address");
        // Without the patient's ABHA there is nothing to check the visits' owner against.
        if (!StringUtils.hasText(oid) && !StringUtils.hasText(requesterAddress)) {
            refuseShare(flowId, transactionId, "Not sent: " + NAMES_NO_ABHA);
            return null;
        }

        List<AbdmHipStore.CareContext> known = store.findCareContextsByReferences(hipId, references);
        // One request is one patient's: visits of more than one, linked or not, are refused whole.
        if (known.stream().map(AbdmHipStore.CareContext::individualId).distinct().count() > 1) {
            failShare(flowId, transactionId, "Not sent: the visits asked for belong to more than one patient", null);
            return null;
        }
        Map<String, AbdmHipStore.CareContext> linkedHere = known.stream().filter(c -> !PENDING.equals(c.status()))
                .collect(Collectors.toMap(AbdmHipStore.CareContext::reference, c -> c, (a, b) -> a, LinkedHashMap::new));
        // Share only what the consent covers. Each care context holds one record type: a visit is OPConsultation.
        // No hi_types means the request does not narrow by type.
        List<String> asked = AbdmFlows.texts(AbdmJson.at(data, "hi_types"), null);
        boolean anyType = asked == null || asked.isEmpty();
        if (!anyType && !linkedHere.isEmpty() && linkedHere.values().stream().noneMatch(c -> asked.contains(c.hiType()))) {
            refuseShare(flowId, transactionId, "Not sent: no record of the types asked for is held here; asked for " + String.join(", ", asked));
            return null;
        }
        return new Request(orgSettings, hipId, requesterKey, requesterNonce, references, patientUuid, oid, requesterAddress, linkedHere, asked);
    }

    // A share that ends FAILED through a fault (an exception, Eka's own failure) is seen: reported, and counted on the
    // status page (AbdmHipStore.failedSharesSince).
    private void failShare(long flowId, String transactionId, String error, Throwable cause) {
        store.finishDataFlow(flowId, FLOW_FAILED, 0, error);
        IllegalStateException failed = new IllegalStateException("Eka data fetch " + transactionId + " not shared: " + error, cause);
        logger.warn(failed.getMessage());
        bugsnag.notify(failed);
    }

    // A request refused on its own grounds, as it should be: counted on the status page, not an alarm.
    private void refuseShare(long flowId, String transactionId, String error) {
        store.finishDataFlow(flowId, FLOW_FAILED, 0, error);
        logger.info("Eka data fetch {} not shared: {}", transactionId, error);
    }

    // Eka may have the records: a failure to note so is reported, never thrown, so the inbox does not send them again.
    private void noteSent(long flowId, String transactionId, String status, int entries, String error) {
        try {
            store.finishDataFlow(flowId, status, entries, error);
        } catch (RuntimeException e) {
            IllegalStateException unnoted = new IllegalStateException("Eka data fetch " + transactionId + " sent, but not recorded as " + status, e);
            logger.error(unnoted.getMessage(), e);
            bugsnag.notify(unnoted);
        }
    }

    // This try's flow is closed as RETRIED, and the failure goes back to the inbox, which tries the share again.
    private RuntimeException retried(long flowId, String transactionId, String error, RuntimeException cause) {
        store.finishDataFlow(flowId, FLOW_RETRIED, 0, error + "; tried again");
        logger.warn("Eka data fetch {}: {}; it will be tried again", transactionId, error);
        return cause;
    }

    // A database fault that may pass on another try: a connection not had or dropped, a transaction not begun, a deadlock
    // or a timeout, however Spring, Hibernate or the driver names it, or Postgres's own code says it (a connection fault,
    // class 08; a serialization failure, 40001; a deadlock, 40P01).
    static boolean transientDatabaseFault(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof TransientDataAccessException || t instanceof RecoverableDataAccessException
                    || t instanceof CannotCreateTransactionException || t instanceof DataAccessResourceFailureException
                    || t instanceof JDBCConnectionException || t instanceof SQLTransientException || t instanceof SQLRecoverableException) {
                return true;
            }
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                String state = sql.getSQLState();
                if (state.startsWith("08") || state.equals("40001") || state.equals("40P01")) return true;
            }
        }
        return false;
    }

    // The verified ABHA's address, else its number's default address: its 14 digits with the environment's suffix.
    static String verifiedAddress(AbhaEvent verified, String abhaAddressSuffix) {
        String address = AbdmFlows.withSuffix(verified.getAbhaAddress(), abhaAddressSuffix);
        if (address != null) return address;
        String number = AbdmFlows.digitsOnly(verified.getAbhaNumber());
        return number.length() == 14 && abhaAddressSuffix != null ? number + abhaAddressSuffix : null;
    }

    private static final String NOT_THEIRS = "the ABHA asking is no longer the patient's";
    private static final String NAMES_NO_ABHA = "the request names no patient's ABHA";

    // Why the ABHA asking may not have the patient's records; null when it may. It must be the ABHA on the record, one Eka
    // verified (its record carries an Eka ID), named by that record itself: its Eka ID when Eka gives one, else its
    // address or its number's default address. The record's typed ABHA answers never count on their own.
    private static String abhaProblem(AbhaLink link, AbhaVerifiedRecords.Verification verification, String requesterOid, String requesterAddress) {
        if (!StringUtils.hasText(requesterOid) && !StringUtils.hasText(requesterAddress)) return NAMES_NO_ABHA;
        if (link == null) return NOT_THEIRS;
        if (verification.heldByAnother()) return AbhaVerifiedRecords.HELD_BY_ANOTHER;
        Optional<AbhaEvent> verified = verification.verified();
        if (verified.isEmpty()) return "the patient's ABHA is not verified with Eka";
        if (StringUtils.hasText(requesterOid)) return requesterOid.equals(verified.get().getEkaOid()) ? null : NOT_THEIRS;
        return AbdmFlows.namesAbha(requesterAddress, verified.get().getAbhaAddress(), verified.get().getAbhaNumber()) ? null : NOT_THEIRS;
    }

    // ---- building

    // Read in one transaction: the encounter, its patient and their ABHA, as a FHIR bundle from the HIP hipId. requesterOid
    // and requesterAddress: the ABHA Eka asks for, which must still be the one on the patient's record. A failure to read
    // is thrown, as another try may read it.
    Prepared prepare(String reference, String expectedPatientUuid, String requesterOid, String requesterAddress, String hipId,
                     EkaSystemConfig orgSettings) {
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
            AbhaVerifiedRecords.Verification verification = link == null ? AbhaVerifiedRecords.Verification.NONE
                    : verifiedRecords.check(individual.getUuid(), link.getAbhaNumber(), link.getAbhaAddress());
            // A visit stays linked at NHA under the ABHA it was linked with; once the record holds another ABHA, or
            // none, or one Eka never verified, that ABHA gets nothing.
            String abhaProblem = abhaProblem(link, verification, requesterOid, requesterAddress);
            if (abhaProblem != null) return new Prepared(reference, null, abhaProblem);
            AbhaEvent verified = verification.record();
            // The record names the patient by the verified ABHA, never by the record's typed answers.
            String number = AbdmFlows.digitsOnly(verified.getAbhaNumber());
            CareContextDocument document = assembler.assemble(encounter, individual,
                    number.isEmpty() ? null : AbdmFlows.hyphenatedAbhaNumber(number),
                    verifiedAddress(verified, orgSettings.getAbhaAddressSuffix()), hipId, orgSettings.getHipName(hipId));
            return new Prepared(reference, AbdmJson.write(builder.build(document)), null);
        });
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
