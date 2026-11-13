package org.avni.server.service.abdm.eka;

import com.bugsnag.Bugsnag;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.abdm.AbdmAccess;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmNotReadyException;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmSettings;
import org.avni.server.service.abdm.AbdmTime;
import org.avni.server.service.abdm.crypto.FideliusCrypto;
import org.avni.server.service.abdm.crypto.FideliusKeyMaterial;
import org.avni.server.service.abdm.hip.AbdmFlows;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abdm.hip.AbdmRetry;
import org.avni.server.service.abdm.hiu.AbdmHiuStore;
import org.avni.server.service.abha.AbhaEventService;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Avni as HIU through Eka Care's ABDM Connect API, spec EKA_ABDM_SPEC.md section 3. Asks for consent
// (consents/create), hears Eka's abha.consent_update and abha.hiu_data_push webhooks, decrypts each entry with our HIU
// key and keeps the FHIR bundle (AbdmHiuStore), which the webapp's HIU screens read.
// Eka pushes the records on its own once consent is granted: Avni sends no health-information request.
// The HIU ID, clinic, doctor and ABHA address suffix are the organisation's (AbdmSettings); the requester comes with each
// request. Field names are read with fallbacks wherever the spec marks them unverified. Never logs an ABHA address, an
// OID, a key, a nonce, a body or a record.
@Component
public class EkaHiuService implements EkaHiuEvents {
    private static final Logger logger = LoggerFactory.getLogger(EkaHiuService.class);

    public static final String KEYSET_PATH = "/abdm/v1/hiu/keyset";
    public static final String CONSENT_CREATE = "/abdm/v1/consents/create";
    public static final String CONSENT_LIST = "/abdm/v1/consents/list";
    // Patients whose consent list the hourly retry asks Eka for, at most, in one run.
    static final int RETRY_PATIENTS = 10;
    public static final String ON_PUSH = "/abdm/v1/hiu/care-context/data/on-push";
    private static final String ENTRY = "/abdm/v1/hiu/care-context/data/%s/entries/%s";

    // The HIU key is kept as an abdm_data_flow row of this consent id (consent_id is not null there).
    public static final String KEYSET_CONSENT_ID = "EKA-HIU-KEYSET";
    private static final String KEYSET_STATUS = "KEYSET";

    public static final String REQUESTED = "REQUESTED";
    public static final String GRANTED = "GRANTED";
    public static final String FAILED = "FAILED";
    public static final String RECEIVED = "RECEIVED";
    public static final String PARTIAL = "PARTIAL";
    // Eka gave no clear answer to the create: the request may be with the patient. Settled from their consent list.
    public static final String UNKNOWN = "UNKNOWN";
    // Eka makes a request within the create call's limit; five minutes allows for Avni's and Eka's clocks.
    static final Duration UNKNOWN_MATCH_WINDOW = Duration.ofMinutes(5);
    // The statuses that end a consent: its records are erased.
    private static final Set<String> ENDING = Set.of("REVOKED", "DENIED", "EXPIRED");
    private static final String LINKED = "LINKED";

    // ABDM's purpose codes to Eka's purpose strings, which must match exactly.
    private static final Map<String, String> PURPOSES = Map.of(
            "CAREMGT", "Care management",
            "PATRQT", "Self Requested",
            "PUBHLTH", "Public Health",
            "DSRCH", "Disease Specific Health Research");
    private static final String DEFAULT_PURPOSE = "Care management";

    private final AbdmSettings settings;
    private final AbdmHiuStore store;
    private final EkaAbdmClient eka;
    private final FideliusCrypto crypto;
    private final EkaCredentialCipher cipher;
    private final AbdmOrgContext orgContext;
    private final AbdmHipStore hipStore;
    private final AbhaEventRepository abhaEventRepository;
    private final AbdmRetry retry;
    private final AbdmAccess access;
    private final Bugsnag bugsnag;
    // Per organisation: the key Eka has been given during this run.
    private final Map<Long, Keyset> registered = new ConcurrentHashMap<>();

    record Keyset(long id, String encryptedPrivateKey, String publicKeyX509, String nonce, Instant expiry) {
        static Keyset of(AbdmHiuStore.DataFlow row) {
            return new Keyset(row.id(), row.encryptedPrivateKey(), row.publicKey(), row.nonce(), row.keyExpiry());
        }
    }

    record EkaPatient(String oid, String subjectUuid, Long individualId) {
        static final EkaPatient NONE = new EkaPatient(null, null, null);
    }

    @Autowired
    public EkaHiuService(AbdmSettings settings, AbdmHiuStore store, EkaAbdmClient eka, FideliusCrypto crypto, EkaCredentialCipher cipher,
                         AbdmOrgContext orgContext, AbdmHipStore hipStore, AbhaEventRepository abhaEventRepository, AbdmRetry retry,
                         AbdmAccess access, Bugsnag bugsnag) {
        this.bugsnag = bugsnag;
        this.settings = settings;
        this.store = store;
        this.eka = eka;
        this.crypto = crypto;
        this.cipher = cipher;
        this.orgContext = orgContext;
        this.hipStore = hipStore;
        this.abhaEventRepository = abhaEventRepository;
        this.retry = retry;
        this.access = access;
    }

    // ---- consent request (spec 3.2). Answers {uuid, status}, plus error when it failed or its outcome is UNKNOWN.

    public Map<String, Object> requestConsent(String abhaAddress, LocalDate from, LocalDate to, List<String> hiTypes,
                                              String purposeCode, String requesterName, String requesterRegistrationNumber) {
        return requestConsent(abhaAddress, from, to, hiTypes, purposeCode, requesterName, requesterRegistrationNumber, null);
    }

    public Map<String, Object> requestConsent(String abhaAddress, LocalDate from, LocalDate to, List<String> hiTypes,
                                              String purposeCode, String requesterName, String requesterRegistrationNumber,
                                              LocalDate expiryDate) {
        EkaSystemConfig orgSettings = settings.current();
        String suffix = orgSettings.getAbhaAddressSuffix();
        String address = AbdmFlows.withSuffix(abhaAddress, suffix);
        if (address == null) throw AbdmRequestException.badRequest("abhaAddress is required");
        if (!isAbhaAddress(address, suffix)) {
            throw AbdmRequestException.badRequest("Not an ABHA address: expected a name of letters, digits, '.' or '_' ending in " + suffix);
        }
        // ABDM names the doctor asking on every consent request.
        if (!StringUtils.hasText(requesterName)) throw AbdmRequestException.badRequest("requesterName is required");
        if (!StringUtils.hasText(requesterRegistrationNumber)) {
            throw AbdmRequestException.badRequest("requesterRegistrationNumber is required");
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate fromDate = from == null ? today.minusYears(5) : from;
        LocalDate toDate = to == null ? today : to;
        if (toDate.isBefore(fromDate)) throw AbdmRequestException.badRequest("'to' is before 'from'");
        List<String> types = hiTypes == null || hiTypes.isEmpty() ? List.of("OPConsultation") : hiTypes;
        String purpose = purposeCode == null || purposeCode.isBlank() ? "CAREMGT" : purposeCode.trim();
        if (!PURPOSES.containsKey(purpose.toUpperCase(Locale.ROOT)) && !PURPOSES.containsValue(purpose)) {
            throw AbdmRequestException.badRequest("Purpose " + purpose + " cannot be asked through Eka; use one of " + String.join(", ", PURPOSES.keySet()));
        }
        if (expiryDate != null && !expiryDate.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw AbdmRequestException.badRequest("'expiry' must be after today");
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant periodFrom = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfTo = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(1);
        // Never a range ending in the future (ABDM-1063).
        Instant periodTo = endOfTo.isAfter(now) ? now : endOfTo;
        // The end of the expiry day asked for, else 30 days from now.
        Instant expiry = expiryDate != null ? expiryDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(1)
                : now.plus(30, ChronoUnit.DAYS);
        String uuid = UUID.randomUUID().toString();
        String initRequestId = UUID.randomUUID().toString();
        // Persist first: the request is on the HIU screen whatever Eka says.
        store.insertConsentRequest(uuid, address, orgSettings.getHiuId(), purpose, String.join(",", types), periodFrom, periodTo, expiry,
                initRequestId, REQUESTED);
        Long rowId = store.findConsentRequestByInitRequest(initRequestId).map(AbdmHiuStore.Consent::id).orElse(null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", uuid);
        try {
            ensureKeyset();
        } catch (RuntimeException e) {
            return failed(out, rowId, uuid, "Our HIU key was not registered with Eka: " + describe(e));
        }

        EkaPatient patient = findPatient(address, abhaAddress);
        if (patient.oid() == null) {
            // sent by ABHA address alone; Eka may refuse it without the patient's OID.
            logger.warn("Eka consent request {}: no Eka OID recorded for the patient; sending the ABHA address only", uuid);
        }
        ObjectNode body = AbdmJson.obj();
        ObjectNode p = body.putObject("patient");
        if (patient.oid() != null) p.put("oid", patient.oid());
        p.put("health_id", address);
        body.put("purpose", purposeText(purpose));
        ArrayNode recordTypes = body.putArray("record_types");
        types.forEach(recordTypes::add);
        ObjectNode period = body.putObject("period");
        period.put("from", rfc3339(periodFrom));
        period.put("to", rfc3339(periodTo));
        period.put("expiry", rfc3339(expiry));
        ObjectNode hiu = body.putObject("hiu");
        putIfText(hiu, "clinic_id", orgSettings.getClinicId());
        putIfText(hiu, "d_oid", orgSettings.getDoctorOid());
        ObjectNode req = hiu.putObject("requester");
        req.put("name", requesterName.trim());
        ObjectNode identifier = req.putObject("identifier");
        identifier.put("system", "https://www.nmc.org.in");
        identifier.put("type", "MR");
        identifier.put("value", requesterRegistrationNumber.trim());
        if (!StringUtils.hasText(orgSettings.getClinicId()) || !StringUtils.hasText(orgSettings.getDoctorOid())) {
            logger.warn("Eka consent request {}: the organisation's Eka settings have no clinicId or doctorOid", uuid);
        }

        // Never sent twice: each create reaches the patient's phone, and approving an earlier one would be lost.
        EkaResponse response;
        try {
            response = eka.postAtMostOnce(CONSENT_CREATE, body, patientHeaders(patient.oid(), patient.subjectUuid()));
        } catch (RuntimeException e) {
            if (EkaAbdmClient.mayHaveActed(e)) {
                return unknown(out, rowId, uuid, "Eka gave no clear answer; the request may have reached the patient: " + describe(e));
            }
            boolean reachedEka = e instanceof EkaCallException call && call.getStatus() > 0;
            return failed(out, rowId, uuid, (reachedEka ? "Eka refused the consent request: " : "The consent request did not reach Eka: ") + describe(e));
        }
        String consentRequestId = consentInitIdOf(response == null ? null : response.body());
        if (consentRequestId == null) {
            // The request may still have reached the patient; without Eka's id, Eka's consent list settles it (UNKNOWN).
            return unknown(out, rowId, uuid, "Eka answered HTTP " + (response == null ? null : response.status())
                    + " with no consent id; the request may have reached the patient");
        }
        if (rowId != null) {
            store.setConsentRequestId(rowId, consentRequestId, REQUESTED);
            // Eka's consent_init_id is not ABDM's consent request id, which consent_update and the push name. Map it now
            // when Eka's list already has it; otherwise consentUpdate maps it the same way when the first update comes.
            String abdmId = abdmIdOf(consentRequestId, patient, address);
            if (abdmId != null && !abdmId.equals(consentRequestId)) {
                store.setConsentRequestId(rowId, abdmId, REQUESTED);
                applyParked(abdmId, address, orgSettings.getHiuId());
            }
        }
        out.put("status", REQUESTED);
        return out;
    }

    private Map<String, Object> unknown(Map<String, Object> out, Long rowId, String uuid, String error) {
        if (rowId != null) store.markConsentUnknown(rowId, error);
        logger.warn("Eka consent request {}: outcome unknown: {}", uuid, error);
        out.put("status", UNKNOWN);
        out.put("error", error);
        return out;
    }

    private Map<String, Object> failed(Map<String, Object> out, Long rowId, String uuid, String error) {
        if (rowId != null) store.failConsent(rowId, error);
        logger.warn("Eka consent request {} failed: {}", uuid, error);
        out.put("status", FAILED);
        out.put("error", error);
        return out;
    }

    // ---- HIU key (spec 3.1)

    // Once per run per organisation: the newest stored key, else a fresh one stored first, sent to Eka.
    // the private key is kept under the Eka credentials key (EkaCredentialCipher); keys are never rotated.
    synchronized Keyset ensureKeyset() {
        Long organisationId = orgContext.organisationId();
        long key = organisationId == null ? 0L : organisationId;
        Keyset keyset = registered.get(key);
        if (keyset != null) return keyset;
        keyset = store.findDataFlowsByConsent(KEYSET_CONSENT_ID).stream().findFirst().map(Keyset::of).orElseGet(this::newKeyset);
        ObjectNode body = AbdmJson.obj();
        // X.509 (SubjectPublicKeyInfo), the form Eka takes.
        body.put("public_key", keyset.publicKeyX509());
        body.put("nonce", keyset.nonce());
        eka.patch(KEYSET_PATH, body, Map.of());
        registered.put(key, keyset);
        logger.info("Eka HIU key {} registered with Eka", keyset.id());
        return keyset;
    }

    private Keyset newKeyset() {
        FideliusKeyMaterial keys = crypto.generateKeyMaterial();
        Instant expiry = Instant.now().plus(365, ChronoUnit.DAYS);
        String encrypted = cipher.encrypt(keys.privateKeyBase64());
        long id = store.insertDataFlow(KEYSET_CONSENT_ID, null, encrypted, keys.publicKeyX509Base64(), keys.nonceBase64(), expiry, KEYSET_STATUS);
        return new Keyset(id, encrypted, keys.publicKeyX509Base64(), keys.nonceBase64(), expiry);
    }

    // Every key a push may have been encrypted to: this run's first, then every stored one, newest first.
    private List<Keyset> decryptionKeys() {
        List<Keyset> keys = new ArrayList<>();
        Long organisationId = orgContext.organisationId();
        Keyset current = registered.get(organisationId == null ? 0L : organisationId);
        if (current != null) keys.add(current);
        for (AbdmHiuStore.DataFlow row : store.findDataFlowsByConsent(KEYSET_CONSENT_ID)) {
            if (current == null || row.id() != current.id()) keys.add(Keyset.of(row));
        }
        return keys;
    }

    // ---- abha.consent_update (spec 3.6)

    @Override
    public void consentUpdate(JsonNode event) {
        JsonNode data = AbdmJson.at(event, "data");
        JsonNode notification = notificationOf(data);
        String consentRequestId = consentRequestIdOf(event);
        String status = normaliseStatus(AbdmFlows.firstNonNull(AbdmJson.text(notification, "status"), AbdmJson.text(data, "status")));
        List<String> artefacts = artefactIdsOf(event);
        if (status == null || (consentRequestId == null && artefacts.isEmpty())) {
            logger.warn("Eka consent update without a status, or without a consent request id or artefact; ignored");
            return;
        }
        if (consentRequestId == null) {
            // A withdrawal or expiry naming only the artefacts: each goes under the request it was granted by.
            Map<String, List<String>> byRequest = new LinkedHashMap<>();
            Map<String, List<String>> behindParked = new LinkedHashMap<>();
            for (String artefact : artefacts) {
                Optional<String> request = store.findArtefact(artefact).map(AbdmHiuStore.Consent::consentRequestId);
                if (request.isPresent()) byRequest.computeIfAbsent(request.get(), k -> new ArrayList<>()).add(artefact);
                else store.parkedReferenceOf(artefact).ifPresent(id -> behindParked.computeIfAbsent(id, k -> new ArrayList<>()).add(artefact));
            }
            // A grant still parked has not made its artefacts yet: the withdrawal is parked behind it and replayed after it.
            behindParked.forEach((id, ids) -> store.parkConsentUpdate(id, status, ids));
            if (byRequest.isEmpty() && behindParked.isEmpty()) {
                logger.warn("Eka consent update ({}) naming only artefacts we do not hold; ignored", status);
            }
            byRequest.forEach((id, ids) -> consentUpdate(id, status, ids));
            return;
        }
        consentUpdate(consentRequestId, status, artefacts);
    }

    // The system user cannot act (EkaEventWorker): the ending is applied to what is held here, and Eka is asked nothing.
    @Override
    public boolean endConsentWithoutLogin(long organisationId, JsonNode event) {
        if (!endsConsent(event)) return false;
        String status = statusOf(event);
        boolean whole = store.endWithoutLogin(organisationId, consentRequestIdOf(event), artefactIdsOf(event), status);
        logger.warn("Eka consent update ({}) applied with no login for organisation {}{}", status, organisationId,
                whole ? "" : "; what is not held here waits for the next try");
        return whole;
    }

    // Whether an abha.consent_update withdraws, refuses or expires its consent: taken even after record sharing is off.
    public static boolean endsConsent(JsonNode event) {
        String status = statusOf(event);
        return status != null && ENDING.contains(status);
    }

    private static String statusOf(JsonNode event) {
        JsonNode data = AbdmJson.at(event, "data");
        JsonNode notification = notificationOf(data);
        return normaliseStatus(AbdmFlows.firstNonNull(AbdmJson.text(notification, "status"), AbdmJson.text(data, "status")));
    }

    // The consent request an abha.consent_update names; null when it names only artefacts.
    public static String consentRequestIdOf(JsonNode event) {
        JsonNode data = AbdmJson.at(event, "data");
        JsonNode notification = notificationOf(data);
        return AbdmFlows.firstNonNull(AbdmJson.text(notification, "consentRequestId"), AbdmJson.text(notification, "consent_request_id"),
                AbdmJson.text(data, "consentRequestId"), AbdmJson.text(data, "consent_request_id"));
    }

    // The artefacts an abha.consent_update names.
    public static List<String> artefactIdsOf(JsonNode event) {
        JsonNode data = AbdmJson.at(event, "data");
        JsonNode notification = notificationOf(data);
        return artefactIds(firstPresent(AbdmJson.at(notification, "consentArtefacts"), AbdmJson.at(notification, "consent_artefacts"),
                AbdmJson.at(data, "consent_artefacts")));
    }

    private static JsonNode notificationOf(JsonNode data) {
        JsonNode notification = AbdmJson.at(data, "notification");
        return notification == null || !notification.isObject() ? data : notification;
    }

    private void consentUpdate(String consentRequestId, String status, List<String> artefacts) {
        Optional<AbdmHiuStore.Consent> request = retry.find(() -> store.findConsentRequest(consentRequestId));
        boolean mappedEarlier = request.isPresent();
        // With record sharing off an ending update is still taken, but Eka is asked nothing.
        if (!mappedEarlier && access.recordSharingOn()) request = matchThroughEka(consentRequestId);
        if (request.isEmpty()) {
            // Never filed under another request: with two waiting, that swapped their approvals (AVNI-24, 26 Oct).
            store.parkConsentUpdate(consentRequestId, status, artefacts);
            logger.warn("Eka consent update ({}) for consent request {}, which no request of ours carries yet: parked", status, consentRequestId);
            return;
        }
        String abhaAddress = request.get().abhaAddress();
        String hiuId = hiuIdOf(request.get());
        // Matching through Eka has applied what was parked for the id; a request mapped earlier may still have some.
        if (mappedEarlier) applyParked(consentRequestId, abhaAddress, hiuId);
        apply(consentRequestId, status, artefacts, abhaAddress, hiuId);
    }

    private void apply(String consentRequestId, String status, List<String> artefacts, String abhaAddress, String hiuId) {
        store.setConsentRequestStatus(consentRequestId, status);
        if (!GRANTED.equals(status)) {
            store.setArtefactStatus(artefacts, status);
            // HIU_FLOW_202: what was received under a withdrawn, refused or expired consent goes now, not at the hourly erase.
            if (ENDING.contains(status)) store.eraseRecordsOfEndedConsents();
            return;
        }
        // No fetch and no health-information request: Eka pushes the records itself (abha.hiu_data_push).
        for (String consentId : artefacts) {
            store.upsertArtefact(consentRequestId, consentId, abhaAddress, hiuId, GRANTED);
        }
        // A grant replayed before a withdrawal after record sharing is off: Eka is asked nothing.
        if (access.recordSharingOn()) keepGrantedTerms(consentRequestId, abhaAddress);
        logger.info("Eka consent request {} {} with {} artefact(s)", consentRequestId, status, artefacts.size());
    }

    // The patient may narrow the dates, record types or expiry when granting (HIU_FLOW_106). Eka's consent list carries
    // what was granted; that is what the HIU shows and keeps to. Left as asked when the list does not answer.
    private void keepGrantedTerms(String consentRequestId, String abhaAddress) {
        if (abhaAddress == null) return;
        EkaPatient patient = findPatient(abhaAddress, abhaAddress);
        if (patient.oid() == null) return;
        JsonNode consents = consentList(patient, abhaAddress);
        if (consents == null) return;
        for (JsonNode c : consents) {
            if (!consentRequestId.equals(AbdmJson.text(c, "consent_id"))) continue;
            List<String> types = AbdmFlows.texts(AbdmJson.at(c, "hi_types"), null);
            store.setGrantedTerms(consentRequestId, types == null || types.isEmpty() ? null : String.join(",", types),
                    instantOf(AbdmJson.text(c, "period.from")), instantOf(AbdmJson.text(c, "period.to")), instantOf(AbdmJson.text(c, "period.expiry")));
            return;
        }
    }

    private static Instant instantOf(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    // Hourly (AbdmHousekeepingJob): an update parked because no request carried its id is applied once one does,
    // whether another path has mapped the request since or Eka's consent list now gives the match. To keep each run short
    // it asks Eka about RETRY_PATIENTS patients at most, those with the newest requests first; an older request is still
    // matched when its push, approval or next update comes.
    public void retryParkedUpdates() {
        List<String> unmatched = new ArrayList<>();
        for (String id : store.parkedConsentRequestIds()) {
            // Told to stop (the server shutting down): what is left waits for the next run.
            if (Thread.currentThread().isInterrupted()) return;
            Optional<AbdmHiuStore.Consent> request = store.findConsentRequest(id);
            if (request.isPresent()) applyParked(id, request.get().abhaAddress(), hiuIdOf(request.get()));
            else unmatched.add(id);
        }
        if (!unmatched.isEmpty()) matchThroughEka(unmatched, RETRY_PATIENTS);
    }

    // Updates parked for this id before it was matched, oldest first. Each is claimed before it is applied, so two paths
    // reaching it at once apply it once; one whose apply fails is released for the next try.
    private void applyParked(String consentRequestId, String abhaAddress, String hiuId) {
        for (AbdmHiuStore.ParkedConsentUpdate parked : store.parkedConsentUpdates(consentRequestId)) {
            if (!store.claimParked(parked.id())) continue;
            try {
                apply(consentRequestId, parked.status(), parked.artefactIds(), abhaAddress, hiuId);
            } catch (RuntimeException e) {
                store.releaseParked(parked.id());
                throw e;
            }
        }
    }

    // Requests Eka gave no clear answer to (UNKNOWN), hourly where record sharing is on: the patient's consent list at Eka
    // settles each one it can tell apart (unknownRequestOf), newest first, RETRY_PATIENTS patients at most. One it cannot
    // stays UNKNOWN for the next run, or for the patient's answer, until it expires.
    public void settleUnknownRequests() {
        Map<String, List<AbdmHiuStore.Consent>> byPatient = new LinkedHashMap<>();
        for (AbdmHiuStore.Consent r : store.unknownConsentRequests()) {
            if (r.abhaAddress() != null) byPatient.computeIfAbsent(r.abhaAddress().toLowerCase(Locale.ROOT), a -> new ArrayList<>()).add(r);
        }
        int asked = 0;
        for (List<AbdmHiuStore.Consent> requests : byPatient.values()) {
            if (asked >= RETRY_PATIENTS || Thread.currentThread().isInterrupted()) break;
            String address = requests.get(0).abhaAddress();
            EkaPatient patient = findPatient(address, address);
            if (patient.oid() == null) continue;
            asked++;
            JsonNode consents = consentList(patient, address);
            if (consents == null) continue;
            for (JsonNode entry : consents) {
                String id = AbdmFlows.firstNonNull(AbdmJson.text(entry, "consent_id"), AbdmJson.text(entry, "consent_init_id"));
                AbdmHiuStore.Consent request = id == null ? null : unknownRequestOf(entry, requests, consents);
                if (request != null) settleUnknown(request, id);
            }
        }
    }

    // Files an UNKNOWN request under the id Eka's list gives it, then applies any update that came for that id meanwhile.
    // False when another path settled it first.
    private boolean settleUnknown(AbdmHiuStore.Consent request, String id) {
        if (!store.settleUnknownConsent(request.id(), id)) return false;
        logger.info("Eka consent request {}, whose create Eka gave no clear answer to, is {} at Eka", request.uuid(), id);
        applyParked(id, request.abhaAddress(), hiuIdOf(request));
        return true;
    }

    // The UNKNOWN request a consent in the patient's list at Eka is, told apart by when Eka made it (c_at): a consent no
    // request of ours carries, made within UNKNOWN_MATCH_WINDOW of exactly one UNKNOWN request, which is that close to no
    // other such consent. Null when that cannot be told: nothing is guessed.
    private AbdmHiuStore.Consent unknownRequestOf(JsonNode entry, List<AbdmHiuStore.Consent> requests, JsonNode consents) {
        Instant made = instantOf(AbdmJson.text(entry, "c_at"));
        if (made == null || carried(entry)) return null;
        List<AbdmHiuStore.Consent> near = requests.stream().filter(r -> UNKNOWN.equals(r.status()) && close(r.createdAt(), made)).toList();
        if (near.size() != 1) return null;
        AbdmHiuStore.Consent request = near.get(0);
        for (JsonNode other : consents) {
            if (other != entry && close(request.createdAt(), instantOf(AbdmJson.text(other, "c_at"))) && !carried(other)) return null;
        }
        return request;
    }

    // Whether a request of ours already carries this consent of the list, by ABDM's id or Eka's.
    private boolean carried(JsonNode entry) {
        String abdmId = AbdmJson.text(entry, "consent_id");
        String initId = AbdmJson.text(entry, "consent_init_id");
        return (abdmId != null && store.findConsentRequest(abdmId).isPresent()) || (initId != null && store.findConsentRequest(initId).isPresent());
    }

    private static boolean close(Instant a, Instant b) {
        return a != null && b != null && Duration.between(a, b).abs().compareTo(UNKNOWN_MATCH_WINDOW) <= 0;
    }

    private Optional<AbdmHiuStore.Consent> matchThroughEka(String abdmId) {
        return Optional.ofNullable(matchThroughEka(List.of(abdmId), Integer.MAX_VALUE).get(abdmId));
    }

    // ABDM's consent request ids to our requests: Eka's consent list, per patient with a request waiting
    // (AbdmHiuStore.waitingConsentRequests, newest first), gives the consent_init_id Eka answered our create with, which
    // is what the request carries until it is matched. A request whose create Eka gave no clear answer to (UNKNOWN)
    // carries none, and is matched by when Eka made the consent (unknownRequestOf). One list per patient, for
    // patientsToAsk patients at most.
    private Map<String, AbdmHiuStore.Consent> matchThroughEka(Collection<String> abdmIds, int patientsToAsk) {
        Map<String, AbdmHiuStore.Consent> matched = new LinkedHashMap<>();
        Map<String, List<AbdmHiuStore.Consent>> byPatient = new LinkedHashMap<>();
        for (AbdmHiuStore.Consent r : store.waitingConsentRequests()) {
            if (r.consentRequestId() == null || r.abhaAddress() == null) continue;
            byPatient.computeIfAbsent(r.abhaAddress().toLowerCase(Locale.ROOT), a -> new ArrayList<>()).add(r);
        }
        for (AbdmHiuStore.Consent r : store.unknownConsentRequests()) {
            if (r.abhaAddress() != null) byPatient.computeIfAbsent(r.abhaAddress().toLowerCase(Locale.ROOT), a -> new ArrayList<>()).add(r);
        }
        int asked = 0;
        for (List<AbdmHiuStore.Consent> requests : byPatient.values()) {
            if (matched.size() == abdmIds.size() || asked >= patientsToAsk) break;
            String address = requests.get(0).abhaAddress();
            EkaPatient patient = findPatient(address, address);
            if (patient.oid() == null) continue;
            asked++;
            JsonNode consents = consentList(patient, address);
            for (String abdmId : abdmIds) {
                if (matched.containsKey(abdmId)) continue;
                String initId = initIdOf(abdmId, consents);
                for (AbdmHiuStore.Consent r : requests) {
                    if (initId != null && initId.equals(r.consentRequestId()) && !matched.containsValue(r)) {
                        mapToAbdmId(r, abdmId);
                        logger.info("Eka consent request {} (Eka's {}) is request {}", abdmId, initId, r.uuid());
                        matched.put(abdmId, r);
                        break;
                    }
                }
                if (matched.containsKey(abdmId)) continue;
                JsonNode entry = entryOf(abdmId, consents);
                AbdmHiuStore.Consent unknown = entry == null ? null : unknownRequestOf(entry, requests, consents);
                if (unknown != null && !matched.containsValue(unknown) && settleUnknown(unknown, abdmId)) matched.put(abdmId, unknown);
            }
        }
        return matched;
    }

    // Files the request under ABDM's id, then applies any update that came for that id before it could be placed.
    private void mapToAbdmId(AbdmHiuStore.Consent request, String abdmId) {
        store.mapConsentRequestId(request.id(), abdmId);
        applyParked(abdmId, request.abhaAddress(), hiuIdOf(request));
    }

    private String hiuIdOf(AbdmHiuStore.Consent request) {
        return request.hiuId() != null ? request.hiuId() : settings.current().getHiuId();
    }

    // ---- abha.hiu_data_push (spec 3.7, 3.8). Safe to run again: a record already kept is not kept twice.

    @Override
    public void hiuDataPush(JsonNode event, boolean lastTry) {
        JsonNode data = AbdmJson.at(event, "data");
        if (data == null || !data.isObject()) {
            logger.warn("Eka HIU data push without data; ignored");
            return;
        }
        String consentId = pushConsentIdOf(event);
        // data.transaction_id names the data flow; the top-level one names the webhook delivery (spec 3.7).
        String dataTransactionId = AbdmFlows.firstNonNull(AbdmJson.text(data, "transaction_id"), AbdmJson.text(data, "transactionId"));
        String transactionId = dataTransactionId != null ? dataTransactionId : AbdmJson.text(event, "transaction_id");
        String hipId = AbdmFlows.firstNonNull(AbdmJson.text(data, "hip_id"), AbdmJson.text(data, "hipId"));
        String oid = AbdmJson.text(data, "oid");
        String partnerPatientId = AbdmJson.text(data, "partner_patient_id");
        String abhaAddress = AbdmJson.text(data, "abha_address");
        JsonNode keyInformation = firstPresent(AbdmJson.at(data, "key_information"), AbdmJson.at(data, "keyMaterial"));
        String senderKey = AbdmFlows.firstNonNull(AbdmJson.text(keyInformation, "dh_public_key.key_value"),
                AbdmJson.text(keyInformation, "dhPublicKey.keyValue"));
        String senderNonce = AbdmJson.text(keyInformation, "nonce");
        if (transactionId == null) {
            logger.warn("Eka HIU data push for consent {} without a transaction id; ignored", consentId);
            return;
        }
        // Eka's push names the consent request; the records and our receipt belong to its granted artefact (AVNI-16).
        String flowConsentId = grantedArtefactFor(consentId, transactionId);
        if (flowConsentId == null) return;

        List<Keyset> keys = decryptionKeys();
        AbdmHiuStore.DataFlow flow = store.findDataFlowByTransaction(transactionId).orElse(null);
        long flowId;
        int earlier;
        if (flow == null) {
            Keyset k = keys.isEmpty() ? null : keys.get(0);
            flowId = store.insertDataFlow(flowConsentId, null, k == null ? null : k.encryptedPrivateKey(), k == null ? null : k.publicKeyX509(),
                    k == null ? null : k.nonce(), k == null ? null : k.expiry(), "RECEIVING");
            store.setTransaction(flowId, transactionId, "RECEIVING", null);
            earlier = 0;
        } else {
            flowId = flow.id();
            earlier = flow.entries() == null ? 0 : flow.entries();
        }

        ArrayNode statusResponse = AbdmJson.arr();
        int received = 0;
        String lastProblem = null;
        JsonNode entries = AbdmJson.at(data, "entries");
        if (entries != null && entries.isArray()) {
            for (JsonNode entry : entries) {
                String careContextId = AbdmFlows.firstNonNull(AbdmJson.text(entry, "care_context_id"), AbdmJson.text(entry, "careContextReference"));
                String problem = null;
                try {
                    if (store.hasHealthInformation(transactionId, careContextId)) {
                        logger.info("Eka HIU data push {}: care context {} already received", transactionId, careContextId);
                    } else {
                        String[] content = contentOf(entry, transactionId);
                        if (content[0] == null) {
                            problem = "No content, inline or fetched";
                        } else if (senderKey == null || senderNonce == null || keys.isEmpty()) {
                            problem = "Key material missing";
                        } else {
                            String plaintext = decrypt(content[0], keys, senderKey, senderNonce);
                            JsonNode bundle = AbdmJson.read(plaintext);
                            if (bundle == null || !bundle.isObject()) throw new IllegalArgumentException("not a JSON object");
                            String checksum = content[1];
                            Boolean matched = checksum == null ? null : AbdmFlows.md5Hex(plaintext).equalsIgnoreCase(checksum);
                            if (Boolean.FALSE.equals(matched)) {
                                // logged, not enforced; the checksum algorithm is undocumented (spec 2.5).
                                logger.warn("Eka HIU data push {}: checksum mismatch for care context {}", transactionId, careContextId);
                            }
                            store.insertHealthInformation(flowId, transactionId, flowConsentId, hipId, careContextId, matched, AbdmJson.write(bundle));
                            received++;
                        }
                    }
                } catch (EkaCallException e) {
                    problem = "Entry not fetched from Eka: " + describe(e);
                } catch (org.avni.server.service.abdm.hiu.AbdmRecordCipher.NoKeyException e) {
                    problem = "Not kept: no key to store received records";
                    logger.error("Eka HIU data push {}: {}", transactionId, e.getMessage());
                } catch (RuntimeException e) {
                    problem = "Could not decrypt or read the record";
                    logger.warn("Eka HIU data push {}: care context {} unreadable: {}", transactionId, careContextId, e.getClass().getSimpleName());
                }
                if (problem != null) lastProblem = problem;
                ObjectNode s = statusResponse.addObject();
                s.put("care_context_id", careContextId);
                s.put("success", problem == null);
                s.put("description", problem == null ? "parsed" : problem);
            }
        }

        int total = earlier + received;
        // Some records kept and some not is PARTIAL, with the last problem, not a full receipt.
        String flowStatus = total == 0 ? FAILED : lastProblem == null ? RECEIVED : PARTIAL;
        store.finishDataFlow(flowId, flowStatus, total, total > 0 ? lastProblem : (lastProblem != null ? lastProblem : "No record could be read"));
        logger.info("Eka HIU data push {} for consent {}: {} record(s) kept", transactionId, consentId, received);

        ObjectNode ack = AbdmJson.obj();
        ack.put("consent_id", flowConsentId);
        ack.put("transaction_id", transactionId);
        ack.set("status_response", statusResponse);
        try {
            eka.post(ON_PUSH, ack, patientHeaders(oid, partnerPatientId));
        } catch (RuntimeException e) {
            // A receipt may be sent twice, so one that may pass goes back to the inbox: the push is tried again whole.
            if (!lastTry && !EkaAbdmClient.refusedForGood(e)) {
                logger.warn("Eka HIU on-push for {} failed: {}; it will be tried again", transactionId, describe(e));
                throw e;
            }
            IllegalStateException failed = new IllegalStateException("Eka HIU on-push for " + transactionId + " not sent: " + describe(e));
            logger.warn(failed.getMessage());
            bugsnag.notify(failed);
        }
    }

    // {content, checksum}: inline, else fetched by content_ref within Eka's 15 minutes (spec 3.7).
    private String[] contentOf(JsonNode entry, String transactionId) {
        String content = AbdmJson.text(entry, "content");
        String checksum = AbdmJson.text(entry, "checksum");
        if (StringUtils.hasText(content)) return new String[]{content, checksum};
        String contentRef = AbdmFlows.firstNonNull(AbdmJson.text(entry, "content_ref"), AbdmJson.text(entry, "contentRef"));
        if (contentRef == null) return new String[]{null, checksum};
        String path = String.format(ENTRY, UriUtils.encodePathSegment(transactionId, StandardCharsets.UTF_8),
                UriUtils.encodePathSegment(contentRef, StandardCharsets.UTF_8));
        EkaResponse response = eka.get(path, Map.of());
        JsonNode body = response == null ? null : response.body();
        String fetched = AbdmFlows.firstNonNull(AbdmJson.text(body, "content"), AbdmJson.text(body, "data.content"));
        String fetchedChecksum = AbdmFlows.firstNonNull(AbdmJson.text(body, "checksum"), AbdmJson.text(body, "data.checksum"), checksum);
        return new String[]{StringUtils.hasText(fetched) ? fetched : null, fetchedChecksum};
    }

    private String decrypt(String content, List<Keyset> keys, String senderKey, String senderNonce) {
        RuntimeException last = null;
        for (Keyset k : keys) {
            try {
                return crypto.decrypt(content, cipher.decrypt(k.encryptedPrivateKey()), k.nonce(), senderKey, senderNonce);
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw last != null ? last : new IllegalStateException("No HIU key");
    }

    // The consent an abha.hiu_data_push names: a consent request of ours, or one of its artefacts.
    public static String pushConsentIdOf(JsonNode event) {
        JsonNode data = AbdmJson.at(event, "data");
        return AbdmFlows.firstNonNull(AbdmJson.text(data, "consent_id"), AbdmJson.text(data, "consentId"));
    }

    // The artefact a push belongs to: the id itself when it is an artefact of ours, else the one granted artefact of the
    // consent request it names. Before the grant is applied the push waits (AbdmNotReadyException), and the inbox tries it
    // again up to its cap. Null, logged, when it names no consent, or a request with several artefacts and not which one.
    private String grantedArtefactFor(String consentId, String transactionId) {
        if (consentId == null) {
            logger.warn("Eka HIU data push {} names no consent; nothing kept, no receipt", transactionId);
            return null;
        }
        if (store.findArtefact(consentId).isPresent()) return consentId;
        Optional<AbdmHiuStore.Consent> request = store.findConsentRequest(consentId);
        if (request.isEmpty()) request = matchThroughEka(consentId);
        if (request.isEmpty()) {
            throw new AbdmNotReadyException("Eka HIU data push " + transactionId + " for consent " + consentId + ": no request of ours carries it yet");
        }
        List<String> granted = store.findArtefacts(List.of(consentId)).stream()
                .filter(a -> GRANTED.equals(a.status())).map(AbdmHiuStore.Artefact::consentId).toList();
        if (granted.isEmpty()) {
            throw new AbdmNotReadyException("Eka HIU data push " + transactionId + " for consent " + consentId + ": no grant applied yet");
        }
        if (granted.size() > 1) {
            logger.warn("Eka HIU data push for consent request {}: {} granted artefacts and the push names none of them", consentId, granted.size());
            return null;
        }
        return granted.get(0);
    }

    // ABDM's consent request id for Eka's consent_init_id, from the patient's consent list; null when not listed.
    private String abdmIdOf(String initId, EkaPatient patient, String abhaAddress) {
        if (initId == null || patient.oid() == null) return null;
        JsonNode consents = consentList(patient, abhaAddress);
        if (consents == null) return null;
        for (JsonNode c : consents) {
            if (initId.equals(AbdmJson.text(c, "consent_init_id"))) return AbdmJson.text(c, "consent_id");
        }
        return null;
    }

    // Eka's consent_init_id for ABDM's consent request id; null when not listed.
    private static String initIdOf(String abdmId, JsonNode consents) {
        JsonNode entry = entryOf(abdmId, consents);
        return entry == null ? null : AbdmJson.text(entry, "consent_init_id");
    }

    // The consent in the patient's list with ABDM's consent request id; null when not listed.
    private static JsonNode entryOf(String abdmId, JsonNode consents) {
        if (consents == null) return null;
        for (JsonNode c : consents) {
            if (abdmId.equals(AbdmJson.text(c, "consent_id"))) return c;
        }
        return null;
    }

    // The patient's consents at Eka (spec 3.3) as an array, or null when Eka does not answer with one.
    private JsonNode consentList(EkaPatient patient, String abhaAddress) {
        try {
            ObjectNode body = AbdmJson.obj();
            ObjectNode p = body.putObject("patient");
            p.put("oid", patient.oid());
            p.put("health_id", abhaAddress);
            EkaSystemConfig orgSettings = settings.current();
            ObjectNode hiu = body.putObject("hiu");
            putIfText(hiu, "clinic_id", orgSettings.getClinicId());
            putIfText(hiu, "d_oid", orgSettings.getDoctorOid());
            EkaResponse response = eka.post(CONSENT_LIST, body, patientHeaders(patient.oid(), patient.subjectUuid()));
            JsonNode consents = response == null ? null
                    : firstPresent(AbdmJson.at(response.body(), "consents"), AbdmJson.at(response.body(), "data.consents"));
            return consents != null && consents.isArray() ? consents : null;
        } catch (RuntimeException e) {
            logger.info("Eka consent list not read: {}", describe(e));
            return null;
        }
    }

    // NHA's pattern for an ABHA address, ending in the suffix of the organisation's Eka environment.
    static boolean isAbhaAddress(String address, String suffix) {
        if (address == null || suffix == null) return false;
        int at = address.lastIndexOf('@');
        if (at < 0) return false;
        String name = address.substring(0, at);
        String domain = address.substring(at);
        return domain.equalsIgnoreCase(suffix) && name.matches("[a-zA-Z0-9][a-zA-Z0-9_.]+[a-zA-Z0-9]");
    }

    // ---- patient

    // The patient's Eka OID: the subject holding this ABHA address and the record of that ABHA among its ABHA events (M1),
    // never a later lookup of another ABHA left unsaved; else the latest ABHA event naming the address. Read-only.
    EkaPatient findPatient(String address, String asTyped) {
        try {
            List<AbdmHipStore.Subject> subjects = hipStore.findSubjectsByAbhaAddress(address, settings.current().getAbhaAddressSuffix());
            if (subjects.size() > 1) {
                // Never guessed between: which of them the request is for cannot be told from the address.
                logger.warn("Eka patient lookup: {} patients hold this ABHA address; none is picked", subjects.size());
                return EkaPatient.NONE;
            }
            for (AbdmHipStore.Subject s : subjects) {
                Optional<AbhaEvent> event = AbhaEventService.ofSavedAbha(
                        abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc(s.uuid()), null, address);
                if (event.isPresent()) return new EkaPatient(event.get().getEkaOid(), s.uuid(), s.id());
            }
            // A lookup naming the address gives the ABHA's Eka ID, whichever record it was made on; the patient is the one
            // holding the address, never the record of a lookup left unsaved.
            Optional<AbdmHiuStore.EkaPatientRow> row = store.findEkaPatientByAbhaAddress(asTyped == null ? List.of(address) : List.of(address, asTyped));
            if (row.isPresent()) {
                AbdmHipStore.Subject holder = subjects.isEmpty() ? null : subjects.get(0);
                return new EkaPatient(row.get().ekaOid(), holder == null ? null : holder.uuid(), holder == null ? null : holder.id());
            }
            if (!subjects.isEmpty()) return new EkaPatient(null, subjects.get(0).uuid(), subjects.get(0).id());
        } catch (RuntimeException e) {
            logger.warn("Eka patient lookup failed: {}", e.getClass().getSimpleName());
        }
        return EkaPatient.NONE;
    }

    // ---- small pieces

    // An HIU call speaks for no one centre: it carries the organisation's first HIP ID.
    private Map<String, String> patientHeaders(String oid, String partnerPatientId) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (StringUtils.hasText(oid)) headers.put(EkaAbdmClient.H_PATIENT_OID, oid);
        if (StringUtils.hasText(partnerPatientId)) headers.put(EkaAbdmClient.H_PARTNER_PATIENT_ID, partnerPatientId);
        String hipId = settings.current().hipIdOr(null);
        if (StringUtils.hasText(hipId)) headers.put(EkaAbdmClient.H_HIP_ID, hipId);
        return headers;
    }

    private static String purposeText(String purpose) {
        String byCode = PURPOSES.get(purpose.toUpperCase(Locale.ROOT));
        if (byCode != null) return byCode;
        if (PURPOSES.containsValue(purpose)) return purpose;
        logger.warn("Purpose {} has no Eka equivalent; sent as '{}'", purpose, DEFAULT_PURPOSE);
        return DEFAULT_PURPOSE;
    }

    // Eka's handle for the request (spec 3.2: consent_init_id), read with fallbacks.
    private static String consentInitIdOf(JsonNode body) {
        if (body == null) return null;
        return AbdmFlows.firstNonNull(AbdmJson.text(body, "consent_init_id"), AbdmJson.text(body, "consentInitId"),
                AbdmJson.text(body, "consent_request_id"), AbdmJson.text(body, "consentRequestId"), AbdmJson.text(body, "consent_id"),
                AbdmJson.text(body, "id"), AbdmJson.text(body, "data.consent_init_id"), AbdmJson.text(body, "data.consent_request_id"),
                AbdmJson.text(body, "data.id"));
    }

    // ABDM's set is GRANTED, DENIED, REVOKED, EXPIRED; Eka's is undocumented (spec 3.6). Upper case, a few synonyms.
    static String normaliseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String s = status.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "APPROVED" -> GRANTED;
            case "REJECTED" -> "DENIED";
            default -> s;
        };
    }

    // consentArtefacts as {id}, [{id}] or ["id"] (spec 3.6: shown as an object, may be an array).
    static List<String> artefactIds(JsonNode node) {
        List<String> ids = new ArrayList<>();
        if (node == null || node.isNull() || node.isMissingNode()) return ids;
        if (node.isArray()) {
            for (JsonNode n : node) {
                String id = n.isValueNode() ? n.asText() : AbdmFlows.firstNonNull(AbdmJson.text(n, "id"), AbdmJson.text(n, "consent_id"));
                if (StringUtils.hasText(id)) ids.add(id);
            }
        } else if (node.isObject()) {
            String id = AbdmFlows.firstNonNull(AbdmJson.text(node, "id"), AbdmJson.text(node, "consent_id"));
            if (StringUtils.hasText(id)) ids.add(id);
        } else if (StringUtils.hasText(node.asText())) {
            ids.add(node.asText());
        }
        return ids;
    }

    private static JsonNode firstPresent(JsonNode... nodes) {
        for (JsonNode n : nodes) if (n != null && !n.isNull() && !n.isMissingNode()) return n;
        return null;
    }

    private static void putIfText(ObjectNode node, String field, String value) {
        if (StringUtils.hasText(value)) node.put(field, value);
    }

    // RFC 3339 UTC without fractions, as Eka's examples write it.
    private static String rfc3339(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static String describe(RuntimeException e) {
        if (e instanceof EkaCallException g) {
            String code = g.getAbdmCode() == null ? "" : g.getAbdmCode() + " ";
            return code + g.getMessage() + " (HTTP " + g.getStatus() + ")";
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
