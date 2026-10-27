package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmOrgContext;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// DEMO-ONLY abdm-hackathon: never merged.
// Avni as HIU through Eka Care's ABDM Connect API, spec EKA_ABDM_SPEC.md section 3. Asks for consent
// (consents/create), hears Eka's abha.consent_update and abha.hiu_data_push webhooks, decrypts each entry with our HIU
// key and keeps the FHIR bundle (AbdmHiuStore), which the webapp's HIU screens read.
// Eka pushes the records on its own once consent is granted: Avni sends no health-information request.
// Field names are read with fallbacks wherever the spec marks them unverified. Never logs an ABHA address, an OID, a
// key, a nonce, a body or a record.
@Component
public class EkaHiuService implements EkaHiuEvents {
    private static final Logger logger = LoggerFactory.getLogger(EkaHiuService.class);

    public static final String KEYSET_PATH = "/abdm/v1/hiu/keyset";
    public static final String CONSENT_CREATE = "/abdm/v1/consents/create";
    public static final String CONSENT_LIST = "/abdm/v1/consents/list";
    public static final String CONSENT_APPROVE = "/abdm/v1/consents/approve";
    public static final String ON_PUSH = "/abdm/v1/hiu/care-context/data/on-push";
    private static final String ENTRY = "/abdm/v1/hiu/care-context/data/%s/entries/%s";

    // The HIU key is kept as an abdm_data_flow row of this consent id (consent_id is not null there). DEMO-ONLY.
    public static final String KEYSET_CONSENT_ID = "EKA-HIU-KEYSET";
    private static final String KEYSET_STATUS = "KEYSET";

    public static final String REQUESTED = "REQUESTED";
    public static final String GRANTED = "GRANTED";
    public static final String FAILED = "FAILED";
    private static final String LINKED = "LINKED";

    // ABDM's purpose codes to Eka's purpose strings, which must match exactly.
    private static final Map<String, String> PURPOSES = Map.of(
            "CAREMGT", "Care management",
            "PATRQT", "Self Requested",
            "PUBHLTH", "Public Health",
            "DSRCH", "Disease Specific Health Research");
    private static final String DEFAULT_PURPOSE = "Care management";

    private final AbdmConfig cfg;
    private final AbdmHiuStore store;
    private final EkaAbdmClient eka;
    private final FideliusCrypto crypto;
    private final EkaCredentialCipher cipher;
    private final AbdmOrgContext orgContext;
    private final AbdmHipStore hipStore;
    private final AbhaEventRepository abhaEventRepository;
    private final AbdmRetry retry;
    private final String keyForm;
    private final String requesterRegistrationNumber;
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
    public EkaHiuService(AbdmConfig cfg, AbdmHiuStore store, EkaAbdmClient eka, FideliusCrypto crypto, EkaCredentialCipher cipher,
                         AbdmOrgContext orgContext, AbdmHipStore hipStore, AbhaEventRepository abhaEventRepository, AbdmRetry retry,
                         @Value("${abdm.eka.hiu-key-form:x509}") String keyForm,
                         @Value("${abdm.eka.requester-registration-number:MH1001}") String requesterRegistrationNumber) {
        this.cfg = cfg;
        this.store = store;
        this.eka = eka;
        this.crypto = crypto;
        this.cipher = cipher;
        this.orgContext = orgContext;
        this.hipStore = hipStore;
        this.abhaEventRepository = abhaEventRepository;
        this.retry = retry;
        this.keyForm = keyForm;
        this.requesterRegistrationNumber = requesterRegistrationNumber;
    }

    // ---- consent request (spec 3.2). Answers {uuid, status}, plus error when it failed.

    public Map<String, Object> requestConsent(String abhaAddress, LocalDate from, LocalDate to, List<String> hiTypes,
                                              String purposeCode, String requesterName) {
        return requestConsent(abhaAddress, from, to, hiTypes, purposeCode, requesterName, null);
    }

    public Map<String, Object> requestConsent(String abhaAddress, LocalDate from, LocalDate to, List<String> hiTypes,
                                              String purposeCode, String requesterName, LocalDate expiryDate) {
        String address = AbdmFlows.withSuffix(abhaAddress, cfg.getAbhaAddressSuffix());
        if (address == null) throw AbdmRequestException.badRequest("abhaAddress is required");
        if (!isAbhaAddress(address, cfg.getAbhaAddressSuffix())) {
            throw AbdmRequestException.badRequest("Not an ABHA address: expected a name of letters, digits, '.' or '_' ending in "
                    + (StringUtils.hasText(cfg.getAbhaAddressSuffix()) ? cfg.getAbhaAddressSuffix() : "@abdm or @sbx"));
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
        String requester = StringUtils.hasText(requesterName) ? requesterName
                : StringUtils.hasText(cfg.getEkaRequesterName()) ? cfg.getEkaRequesterName() : "Dr. Demo";

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
        store.insertConsentRequest(uuid, address, cfg.getHiuId(), purpose, String.join(",", types), periodFrom, periodTo, expiry,
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
            // DEMO-ONLY: sent by ABHA address alone; Eka may refuse it without the patient's OID.
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
        putIfText(hiu, "clinic_id", cfg.getEkaClinicId());
        putIfText(hiu, "d_oid", cfg.getEkaDoctorOid());
        ObjectNode req = hiu.putObject("requester");
        req.put("name", requester);
        if (StringUtils.hasText(requesterRegistrationNumber)) {
            ObjectNode identifier = req.putObject("identifier");
            identifier.put("system", "https://www.nmc.org.in");
            identifier.put("type", "MR");
            identifier.put("value", requesterRegistrationNumber);
        }
        if (!StringUtils.hasText(cfg.getEkaClinicId()) || !StringUtils.hasText(cfg.getEkaDoctorOid())) {
            logger.warn("Eka consent request {}: abdm.eka.clinic-id or abdm.eka.doctor-oid is not set", uuid);
        }

        try {
            EkaResponse response = eka.postSlow(CONSENT_CREATE, body, patientHeaders(patient.oid(), patient.subjectUuid()));
            String consentRequestId = consentInitIdOf(response == null ? null : response.body());
            if (consentRequestId == null) {
                // Any 2xx is success (the spec's 204-with-a-body contradiction); abha.consent_update correlates it later.
                logger.warn("Eka consent request {}: Eka answered {} with no consent id; waiting for abha.consent_update", uuid,
                        response == null ? null : response.status());
            } else if (rowId != null) {
                store.setConsentRequestId(rowId, consentRequestId, REQUESTED);
                // Eka's consent_init_id is not ABDM's consent request id, which consent_update and the push name. Map it now
                // when Eka's list already has it; otherwise consentUpdate maps it the same way when the first update comes.
                String abdmId = abdmIdOf(consentRequestId, patient, address);
                if (abdmId != null && !abdmId.equals(consentRequestId)) {
                    store.setConsentRequestId(rowId, abdmId, REQUESTED);
                    applyParked(abdmId, address, cfg.getHiuId());
                }
            }
        } catch (RuntimeException e) {
            return failed(out, rowId, uuid, "Eka refused the consent request: " + describe(e));
        }
        out.put("status", REQUESTED);
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
    // DEMO-ONLY: the private key is kept under the Eka credentials key (EkaCredentialCipher); keys are never rotated.
    synchronized Keyset ensureKeyset() {
        Long organisationId = orgContext.organisationId();
        long key = organisationId == null ? 0L : organisationId;
        Keyset keyset = registered.get(key);
        if (keyset != null) return keyset;
        keyset = store.findDataFlowsByConsent(KEYSET_CONSENT_ID).stream().findFirst().map(Keyset::of).orElseGet(this::newKeyset);
        ObjectNode body = AbdmJson.obj();
        body.put("public_key", rawKeyForm() ? rawPoint(keyset.publicKeyX509()) : keyset.publicKeyX509());
        body.put("nonce", keyset.nonce());
        eka.patch(KEYSET_PATH, body, Map.of());
        registered.put(key, keyset);
        logger.info("Eka HIU key {} registered with Eka ({})", keyset.id(), rawKeyForm() ? "raw point" : "X.509");
        return keyset;
    }

    private Keyset newKeyset() {
        FideliusKeyMaterial keys = crypto.generateKeyMaterial();
        Instant expiry = Instant.now().plus(365, ChronoUnit.DAYS);
        String encrypted = cipher.encrypt(keys.privateKeyBase64());
        long id = store.insertDataFlow(KEYSET_CONSENT_ID, null, encrypted, keys.publicKeyX509Base64(), keys.nonceBase64(), expiry, KEYSET_STATUS);
        return new Keyset(id, encrypted, keys.publicKeyX509Base64(), keys.nonceBase64(), expiry);
    }

    private boolean rawKeyForm() {
        return "raw".equalsIgnoreCase(keyForm == null ? null : keyForm.trim());
    }

    private static String rawPoint(String x509Base64) {
        org.bouncycastle.jce.interfaces.ECPublicKey key = (org.bouncycastle.jce.interfaces.ECPublicKey) FideliusCrypto.parsePublicKey(x509Base64);
        return Base64.getEncoder().encodeToString(key.getQ().getEncoded(false));
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
        JsonNode notification = AbdmJson.at(data, "notification");
        if (notification == null || !notification.isObject()) notification = data;
        String consentRequestId = AbdmFlows.firstNonNull(AbdmJson.text(notification, "consentRequestId"),
                AbdmJson.text(notification, "consent_request_id"), AbdmJson.text(data, "consentRequestId"),
                AbdmJson.text(data, "consent_request_id"));
        String status = normaliseStatus(AbdmFlows.firstNonNull(AbdmJson.text(notification, "status"), AbdmJson.text(data, "status")));
        List<String> artefacts = artefactIds(firstPresent(AbdmJson.at(notification, "consentArtefacts"),
                AbdmJson.at(notification, "consent_artefacts"), AbdmJson.at(data, "consent_artefacts")));
        if (consentRequestId == null || status == null) {
            logger.warn("Eka consent update without a consent request id or status; ignored");
            return;
        }

        Optional<AbdmHiuStore.Consent> request = retry.find(() -> store.findConsentRequest(consentRequestId));
        boolean mappedEarlier = request.isPresent();
        if (!mappedEarlier) request = matchThroughEka(consentRequestId);
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
            return;
        }
        // No fetch and no health-information request: Eka pushes the records itself (abha.hiu_data_push).
        for (String consentId : artefacts) {
            store.upsertArtefact(consentRequestId, consentId, abhaAddress, hiuId, GRANTED);
        }
        keepGrantedTerms(consentRequestId, abhaAddress);
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
    // whether another path has mapped the request since or Eka's consent list now gives the match.
    public void retryParkedUpdates() {
        List<String> unmatched = new ArrayList<>();
        for (String id : store.parkedConsentRequestIds()) {
            Optional<AbdmHiuStore.Consent> request = store.findConsentRequest(id);
            if (request.isPresent()) applyParked(id, request.get().abhaAddress(), hiuIdOf(request.get()));
            else unmatched.add(id);
        }
        if (!unmatched.isEmpty()) matchThroughEka(unmatched);
    }

    // Updates parked for this id before it was matched, oldest first.
    private void applyParked(String consentRequestId, String abhaAddress, String hiuId) {
        for (AbdmHiuStore.ParkedConsentUpdate parked : store.parkedConsentUpdates(consentRequestId)) {
            apply(consentRequestId, parked.status(), parked.artefactIds(), abhaAddress, hiuId);
            store.resolveParked(parked.id());
        }
    }

    private Optional<AbdmHiuStore.Consent> matchThroughEka(String abdmId) {
        return Optional.ofNullable(matchThroughEka(List.of(abdmId)).get(abdmId));
    }

    // ABDM's consent request ids to our requests: Eka's consent list, per patient with a request waiting, gives the
    // consent_init_id Eka answered our create with, which is what the request carries until it is matched. Only a
    // request still REQUESTED can be waiting; an answered one is filed under ABDM's id already. One list per patient.
    private Map<String, AbdmHiuStore.Consent> matchThroughEka(Collection<String> abdmIds) {
        Map<String, AbdmHiuStore.Consent> matched = new LinkedHashMap<>();
        Map<String, List<AbdmHiuStore.Consent>> byPatient = new LinkedHashMap<>();
        for (AbdmHiuStore.Consent r : store.listConsentRequests()) {
            if (r.consentRequestId() == null || r.abhaAddress() == null || !REQUESTED.equals(r.status())) continue;
            byPatient.computeIfAbsent(r.abhaAddress().toLowerCase(Locale.ROOT), a -> new ArrayList<>()).add(r);
        }
        for (List<AbdmHiuStore.Consent> requests : byPatient.values()) {
            if (matched.size() == abdmIds.size()) break;
            String address = requests.get(0).abhaAddress();
            EkaPatient patient = findPatient(address, address);
            if (patient.oid() == null) continue;
            JsonNode consents = consentList(patient, address);
            for (String abdmId : abdmIds) {
                if (matched.containsKey(abdmId)) continue;
                String initId = initIdOf(abdmId, consents);
                if (initId == null) continue;
                for (AbdmHiuStore.Consent r : requests) {
                    if (initId.equals(r.consentRequestId()) && !matched.containsValue(r)) {
                        mapToAbdmId(r, abdmId);
                        logger.info("Eka consent request {} (Eka's {}) is request {}", abdmId, initId, r.uuid());
                        matched.put(abdmId, r);
                        break;
                    }
                }
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
        return request.hiuId() != null ? request.hiuId() : cfg.getHiuId();
    }

    // ---- abha.hiu_data_push (spec 3.7, 3.8)

    @Override
    public void hiuDataPush(JsonNode event) {
        JsonNode data = AbdmJson.at(event, "data");
        if (data == null || !data.isObject()) {
            logger.warn("Eka HIU data push without data; ignored");
            return;
        }
        String consentId = AbdmFlows.firstNonNull(AbdmJson.text(data, "consent_id"), AbdmJson.text(data, "consentId"));
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
        String flowConsentId = grantedArtefactFor(consentId);
        if (flowConsentId == null) {
            logger.warn("Eka HIU data push {} for consent {}: no granted consent of ours covers it; nothing kept, no receipt", transactionId, consentId);
            return;
        }

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
                                // DEMO-ONLY: logged, not enforced; the checksum algorithm is undocumented (spec 2.5).
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
        store.finishDataFlow(flowId, total > 0 ? "RECEIVED" : FAILED, total,
                total > 0 ? null : (lastProblem != null ? lastProblem : "No record could be read"));
        logger.info("Eka HIU data push {} for consent {}: {} record(s) kept", transactionId, consentId, received);

        ObjectNode ack = AbdmJson.obj();
        ack.put("consent_id", flowConsentId);
        ack.put("transaction_id", transactionId);
        ack.set("status_response", statusResponse);
        try {
            eka.post(ON_PUSH, ack, patientHeaders(oid, partnerPatientId));
        } catch (RuntimeException e) {
            logger.warn("Eka HIU on-push for {} failed: {}", transactionId, describe(e));
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

    // The artefact a push belongs to: the id itself when it is an artefact of ours, else the one granted artefact of the
    // consent request it names. consent_update may still be on its way. Null when none, or
    // when the request has several and the push cannot say which (no guessing).
    private String grantedArtefactFor(String consentId) {
        if (consentId == null) return null;
        if (store.findArtefact(consentId).isPresent()) return consentId;
        Optional<AbdmHiuStore.Consent> request = store.findConsentRequest(consentId);
        if (request.isEmpty()) request = matchThroughEka(consentId);
        if (request.isEmpty()) return null;
        Optional<List<String>> granted = retry.find(() -> {
            List<String> ids = store.findArtefacts(List.of(consentId)).stream()
                    .filter(a -> GRANTED.equals(a.status())).map(AbdmHiuStore.Artefact::consentId).toList();
            return ids.isEmpty() ? Optional.empty() : Optional.of(ids);
        });
        if (granted.isEmpty()) return null;
        if (granted.get().size() > 1) {
            logger.warn("Eka HIU data push for consent request {}: {} granted artefacts and the push names none of them", consentId, granted.get().size());
            return null;
        }
        return granted.get().get(0);
    }

    // ---- sandbox approval (spec 3.5): acting as the patient, as a PHR app would

    public Map<String, Object> approveInSandbox(String consentRequestId) {
        return approveInSandbox(consentRequestId, null);
    }

    // overrides (optional): {hipId, careContexts: [{id, display}] or ["id", ...]}. Default: our HIP and the patient's
    // care contexts linked there. Eka's answer, or its refusal, comes back as it came.
    public Map<String, Object> approveInSandbox(String consentRequestIdOrUuid, JsonNode overrides) {
        AbdmHiuStore.Consent request = store.findConsentRequest(consentRequestIdOrUuid)
                .or(() -> store.findConsentRequestByUuid(consentRequestIdOrUuid))
                .orElseThrow(() -> AbdmRequestException.notFound("Consent request not found"));
        if (request.consentRequestId() == null) {
            throw AbdmRequestException.conflict("Eka has not given this consent request an id yet");
        }
        EkaPatient patient = findPatient(request.abhaAddress(), request.abhaAddress());
        if (patient.oid() == null) {
            throw AbdmRequestException.badRequest("No Eka OID is recorded for this patient's ABHA; create or verify it through Eka first");
        }
        String consentId = abdmConsentId(request, patient);
        List<String> hiTypes = request.hiTypes() == null ? List.of("OPConsultation")
                : Arrays.stream(request.hiTypes().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        String hipId = AbdmFlows.firstNonNull(AbdmJson.text(overrides, "hipId"), cfg.getHipId());
        Instant now = Instant.now();
        String from = AbdmTime.format(request.dateFrom() != null ? request.dateFrom() : now.minus(3650, ChronoUnit.DAYS));
        String to = AbdmTime.format(request.dateTo() != null ? request.dateTo() : now);
        String eraseAt = AbdmTime.format(request.dataEraseAt() != null ? request.dataEraseAt() : now.plus(30, ChronoUnit.DAYS));

        ObjectNode body = AbdmJson.obj();
        body.put("id", consentId);
        body.put("access_mode", "view");
        ArrayNode types = body.putArray("hi_types");
        hiTypes.forEach(types::add);
        ObjectNode duration = body.putObject("duration");
        duration.put("from", from);
        duration.put("to", to);
        body.put("erase_at", eraseAt);
        ObjectNode artefact = body.putArray("consent_artefacts").addObject();
        artefact.put("hip_id", hipId);
        artefact.put("access_mode", "view");
        artefact.set("hi_types", types.deepCopy());
        artefact.set("duration", duration.deepCopy());
        artefact.put("erase_at", eraseAt);
        artefact.set("care_contexts", careContexts(overrides, patient, hipId));

        String path = CONSENT_APPROVE + "?oid=" + UriUtils.encodeQueryParam(patient.oid(), StandardCharsets.UTF_8);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("consentRequestId", consentId);
        try {
            EkaResponse response = eka.postSlow(path, body, patientHeaders(patient.oid(), patient.subjectUuid()));
            out.put("ok", true);
            out.put("httpStatus", response == null ? null : response.status());
            out.put("body", response == null ? null : response.body());
        } catch (EkaCallException e) {
            out.put("ok", false);
            out.put("httpStatus", e.getStatus());
            out.put("abdmCode", e.getAbdmCode());
            out.put("error", e.getMessage());
            if (e.getStatus() == 491) {
                out.put("hint", "The patient's ABHA session at Eka has lapsed: log the patient in again (ABHA login or session verify), then approve");
            }
        } catch (RuntimeException e) {
            out.put("ok", false);
            out.put("error", describe(e));
        }
        logger.info("Eka sandbox approval of consent request {}: {}", consentId, out.get("ok"));
        return out;
    }

    private ArrayNode careContexts(JsonNode overrides, EkaPatient patient, String hipId) {
        ArrayNode out = AbdmJson.arr();
        JsonNode given = AbdmJson.at(overrides, "careContexts");
        if (given != null && given.isArray()) {
            for (JsonNode c : given) {
                String id = c.isValueNode() ? c.asText() : AbdmFlows.firstNonNull(AbdmJson.text(c, "id"), AbdmJson.text(c, "reference"));
                if (id == null) continue;
                ObjectNode n = out.addObject();
                n.put("id", id);
                n.put("display", c.isValueNode() ? id : AbdmFlows.firstNonNull(AbdmJson.text(c, "display"), id));
            }
            return out;
        }
        if (patient.individualId() == null) return out;
        try {
            for (AbdmHipStore.CareContext c : hipStore.findCareContexts(patient.individualId(), hipId)) {
                if (!LINKED.equalsIgnoreCase(c.status())) continue;
                ObjectNode n = out.addObject();
                n.put("id", c.reference());
                n.put("display", c.display() != null ? c.display() : c.reference());
            }
        } catch (RuntimeException e) {
            logger.warn("Eka sandbox approval: linked care contexts not read: {}", e.getClass().getSimpleName());
        }
        return out;
    }

    // Eka's consent list maps its consent_init_id to ABDM's consent id (spec 3.3); when they differ, ABDM's is kept.
    private String abdmConsentId(AbdmHiuStore.Consent request, EkaPatient patient) {
        String id = request.consentRequestId();
        String abdmId = abdmIdOf(id, patient, request.abhaAddress());
        if (abdmId == null || abdmId.equals(id)) return id;
        mapToAbdmId(request, abdmId);
        logger.info("Eka consent request {} is ABDM consent request {}", id, abdmId);
        return abdmId;
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
        if (consents == null) return null;
        for (JsonNode c : consents) {
            if (abdmId.equals(AbdmJson.text(c, "consent_id"))) return AbdmJson.text(c, "consent_init_id");
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
            ObjectNode hiu = body.putObject("hiu");
            putIfText(hiu, "clinic_id", cfg.getEkaClinicId());
            putIfText(hiu, "d_oid", cfg.getEkaDoctorOid());
            EkaResponse response = eka.post(CONSENT_LIST, body, patientHeaders(patient.oid(), patient.subjectUuid()));
            JsonNode consents = response == null ? null
                    : firstPresent(AbdmJson.at(response.body(), "consents"), AbdmJson.at(response.body(), "data.consents"));
            return consents != null && consents.isArray() ? consents : null;
        } catch (RuntimeException e) {
            logger.info("Eka consent list not read: {}", describe(e));
            return null;
        }
    }

    // NHA's pattern for an ABHA address, with the environment's suffix (@sbx in the sandbox, @abdm in production).
    static boolean isAbhaAddress(String address, String suffix) {
        if (address == null) return false;
        int at = address.lastIndexOf('@');
        if (at < 0) return false;
        String name = address.substring(0, at);
        String domain = address.substring(at);
        boolean domainOk = StringUtils.hasText(suffix) ? domain.equalsIgnoreCase(suffix) : domain.matches("@(abdm|sbx)");
        return domainOk && name.matches("[a-zA-Z0-9][a-zA-Z0-9_.]+[a-zA-Z0-9]");
    }

    // ---- patient

    // The patient's Eka OID: the subject holding this ABHA address and its latest ABHA event (M1), else the latest ABHA
    // event naming the address. Read-only.
    EkaPatient findPatient(String address, String asTyped) {
        try {
            List<AbdmHipStore.Subject> subjects = hipStore.findSubjectsByAbhaAddress(address, cfg.getAbhaAddressSuffix());
            if (subjects.size() > 1) {
                // Never guessed between: which of them the request is for cannot be told from the address.
                logger.warn("Eka patient lookup: {} patients hold this ABHA address; none is picked", subjects.size());
                return EkaPatient.NONE;
            }
            for (AbdmHipStore.Subject s : subjects) {
                Optional<AbhaEvent> event = abhaEventRepository.findFirstBySubjectUuidOrderByCreatedDateTimeDescIdDesc(s.uuid())
                        .filter(e -> !AbhaEventService.REMOVED.equals(e.getAction()) && StringUtils.hasText(e.getEkaOid()));
                if (event.isPresent()) return new EkaPatient(event.get().getEkaOid(), s.uuid(), s.id());
            }
            Optional<AbdmHiuStore.EkaPatientRow> row = store.findEkaPatientByAbhaAddress(asTyped == null ? List.of(address) : List.of(address, asTyped));
            if (row.isPresent()) {
                Long individualId = hipStore.findSubjectByUuid(row.get().subjectUuid()).map(AbdmHipStore.Subject::id).orElse(null);
                return new EkaPatient(row.get().ekaOid(), row.get().subjectUuid(), individualId);
            }
            if (!subjects.isEmpty()) return new EkaPatient(null, subjects.get(0).uuid(), subjects.get(0).id());
        } catch (RuntimeException e) {
            logger.warn("Eka patient lookup failed: {}", e.getClass().getSimpleName());
        }
        return EkaPatient.NONE;
    }

    // ---- small pieces

    private Map<String, String> patientHeaders(String oid, String partnerPatientId) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (StringUtils.hasText(oid)) headers.put(EkaAbdmClient.H_PATIENT_OID, oid);
        if (StringUtils.hasText(partnerPatientId)) headers.put(EkaAbdmClient.H_PARTNER_PATIENT_ID, partnerPatientId);
        if (StringUtils.hasText(cfg.getHipId())) headers.put(EkaAbdmClient.H_HIP_ID, cfg.getHipId());
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
