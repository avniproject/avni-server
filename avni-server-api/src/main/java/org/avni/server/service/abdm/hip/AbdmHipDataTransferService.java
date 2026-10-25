package org.avni.server.service.abdm.hip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.domain.AbhaLink;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.service.abdm.AbdmAsync;
import org.avni.server.service.abdm.AbdmCallbackHeaders;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.AbdmTime;
import org.avni.server.service.abdm.crypto.FideliusCrypto;
import org.avni.server.service.abdm.crypto.FideliusKeyMaterial;
import org.avni.server.service.abdm.fhir.CareContextDocument;
import org.avni.server.service.abdm.fhir.CareContextDocumentAssembler;
import org.avni.server.service.abdm.fhir.FhirBundleBuilder;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// DEMO-ONLY abdm-hackathon: never merged.
// The HIP's answer to a health-information request: check the consent, acknowledge, then (off the callback thread) build
// one FHIR document per consented visit, encrypt it to the requester's key, push it to their dataPushUrl and tell the
// gateway how it went.
@Service
public class AbdmHipDataTransferService {
    private static final Logger logger = LoggerFactory.getLogger(AbdmHipDataTransferService.class);

    public static final String MEDIA = "application/fhir+json";

    private final AbdmConfig cfg;
    private final AbdmHipStore store;
    private final AbdmGatewayClient gateway;
    private final AbdmAsync async;
    private final AbdmOrgContext orgContext;
    private final EncounterRepository encounterRepository;
    private final AbhaLinkRepository abhaLinkRepository;
    private final CareContextDocumentAssembler assembler;
    private final FhirBundleBuilder builder;
    private final FideliusCrypto crypto;

    public AbdmHipDataTransferService(AbdmConfig cfg, AbdmHipStore store, AbdmGatewayClient gateway, AbdmAsync async,
                                      AbdmOrgContext orgContext, EncounterRepository encounterRepository,
                                      AbhaLinkRepository abhaLinkRepository, CareContextDocumentAssembler assembler,
                                      FhirBundleBuilder builder, FideliusCrypto crypto) {
        this.cfg = cfg;
        this.store = store;
        this.gateway = gateway;
        this.async = async;
        this.orgContext = orgContext;
        this.encounterRepository = encounterRepository;
        this.abhaLinkRepository = abhaLinkRepository;
        this.assembler = assembler;
        this.builder = builder;
        this.crypto = crypto;
    }

    // What the requester asked for, taken off the callback body.
    record Request(String transactionId, String consentId, Instant from, Instant to, String dataPushUrl, String requesterKey,
                   String requesterNonce) {
    }

    // One visit's outcome: a bundle to send, or why not.
    record Prepared(String reference, String bundleJson, String problem) {
    }

    public void healthInformationRequest(JsonNode body, AbdmCallbackHeaders headers) {
        JsonNode hi = AbdmJson.at(body, "hiRequest");
        Request request = new Request(
                AbdmFlows.firstNonNull(AbdmJson.text(body, "transactionId"), AbdmJson.text(hi, "transactionId")),
                AbdmJson.text(hi, "consent.id"),
                AbdmTime.parse(AbdmJson.text(hi, "dateRange.from")),
                AbdmTime.parse(AbdmJson.text(hi, "dateRange.to")),
                AbdmJson.text(hi, "dataPushUrl"),
                AbdmJson.text(hi, "keyMaterial.dhPublicKey.keyValue"),
                AbdmJson.text(hi, "keyMaterial.nonce"));

        AbdmHipStore.Consent consent = store.findConsent(request.consentId()).orElse(null);
        String problemCode = null, problem = null;
        if (consent == null) {
            problemCode = "CONSENT_NOT_FOUND";
            problem = "No consent with this id at this HIP";
        } else if (!AbdmHipConsentService.GRANTED.equals(consent.status())) {
            problemCode = "CONSENT_NOT_GRANTED";
            problem = "Consent is " + consent.status();
        } else if (request.dataPushUrl() == null || request.requesterKey() == null || request.requesterNonce() == null) {
            problemCode = "REQUEST_INCOMPLETE";
            problem = "dataPushUrl or keyMaterial missing";
        }

        if (problem != null) {
            logger.info("ABDM health information request {} refused: {}", request.transactionId(), problem);
            if (consent != null) {
                store.insertDataFlow(consent.consentId(), request.transactionId(), headers.requestId(), request.dataPushUrl(), "FAILED", problem);
            }
            replyOnRequest(request.transactionId(), "FAILED", AbdmFlows.error(problemCode, problem), headers);
            return;
        }

        long flowId = store.insertDataFlow(consent.consentId(), request.transactionId(), headers.requestId(), request.dataPushUrl(),
                "ACKNOWLEDGED", null);
        replyOnRequest(request.transactionId(), "ACKNOWLEDGED", null, headers);
        async.submit("ABDM HIP data transfer", () -> transfer(flowId, consent, request));
    }

    private void replyOnRequest(String transactionId, String sessionStatus, ObjectNode error, AbdmCallbackHeaders headers) {
        ObjectNode reply = AbdmJson.obj();
        ObjectNode hiRequest = reply.putObject("hiRequest");
        hiRequest.put("transactionId", transactionId);
        hiRequest.put("sessionStatus", sessionStatus);
        if (error != null) reply.set("error", error);
        reply.set("response", AbdmFlows.responseTo(headers.requestId()));
        try {
            gateway.post(AbdmPaths.OUT_HIP_HI_ON_REQUEST, AbdmGatewayClient.newRequestId(), reply, Map.of(AbdmPaths.H_HIP_ID, cfg.getHipId()));
        } catch (RuntimeException e) {
            logger.warn("ABDM HIP health information on-request for {} failed: {}", transactionId, e.getMessage());
        }
    }

    void transfer(long flowId, AbdmHipStore.Consent consent, Request request) {
        List<Prepared> prepared = new ArrayList<>();
        ArrayNode entries = AbdmJson.arr();
        String flowError = null;
        boolean pushed = false;
        try {
            FideliusKeyMaterial ours = crypto.generateKeyMaterial();
            for (String reference : AbdmHipConsentService.careContextRefs(consent.consentDetail())) {
                Prepared p = prepare(reference, patientReferenceOf(consent.consentDetail(), reference), request);
                if (p.bundleJson() != null) {
                    try {
                        ObjectNode entry = AbdmJson.obj();
                        entry.put("content", crypto.encrypt(p.bundleJson(), ours.privateKeyBase64(), ours.nonceBase64(),
                                request.requesterKey(), request.requesterNonce()));
                        entry.put("media", MEDIA);
                        entry.put("checksum", AbdmFlows.md5Hex(p.bundleJson()));
                        entry.put("careContextReference", reference);
                        entries.add(entry);
                    } catch (RuntimeException e) {
                        p = new Prepared(reference, null, "Could not encrypt the record");
                        logger.warn("ABDM HIP encryption failed for care context {}: {}", reference, e.getClass().getSimpleName());
                    }
                }
                prepared.add(p);
            }
            if (!entries.isEmpty()) {
                ObjectNode push = AbdmJson.obj();
                push.put("pageNumber", 1);
                push.put("pageCount", 1);
                push.put("transactionId", request.transactionId());
                push.set("entries", entries);
                push.set("keyMaterial", AbdmFlows.keyMaterial(ours.publicKeyX509Base64(), ours.nonceBase64(),
                        Instant.now().plus(Duration.ofHours(1))));
                try {
                    gateway.pushToUrl(request.dataPushUrl(), push);
                    pushed = true;
                } catch (RuntimeException e) {
                    flowError = "Push to the HIU failed: " + e.getMessage();
                    logger.warn("ABDM HIP data push for transaction {} failed: {}", request.transactionId(), e.getMessage());
                }
            } else {
                flowError = "No record could be sent";
            }
        } catch (RuntimeException e) {
            flowError = "Transfer failed: " + e.getClass().getSimpleName();
            logger.error("ABDM HIP transfer for transaction {} failed", request.transactionId(), e);
        }

        int delivered = pushed ? entries.size() : 0;
        String sessionStatus = pushed ? "TRANSFERRED" : "FAILED";
        ArrayNode statusResponses = AbdmJson.arr();
        for (Prepared p : prepared) {
            ObjectNode s = statusResponses.addObject();
            s.put("careContextReference", p.reference());
            boolean ok = pushed && p.bundleJson() != null;
            s.put("hiStatus", ok ? "DELIVERED" : "ERRORED");
            s.put("description", ok ? "Delivered" : (p.problem() != null ? p.problem() : flowError));
        }
        store.finishDataFlow(flowId, sessionStatus, delivered, flowError);
        notifyGateway(consent.consentId(), request.transactionId(), sessionStatus, statusResponses);
    }

    // Read in one transaction: the encounter, its patient and their ABHA, as a FHIR bundle.
    private Prepared prepare(String reference, String patientReference, Request request) {
        try {
            return orgContext.inReadOnlyTransaction(() -> {
                Encounter encounter = encounterRepository.findByUuid(reference);
                if (encounter == null || encounter.isVoided() || encounter.getEncounterDateTime() == null) {
                    return new Prepared(reference, null, "Care context not found");
                }
                Individual individual = encounter.getIndividual();
                if (patientReference != null && !patientReference.equals(individual.getUuid())) {
                    return new Prepared(reference, null, "Care context belongs to another patient");
                }
                Instant at = encounter.getEncounterDateTime().toDate().toInstant();
                if ((request.from() != null && at.isBefore(request.from())) || (request.to() != null && at.isAfter(request.to()))) {
                    return new Prepared(reference, null, "Outside the requested date range");
                }
                AbhaLink link = abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(individual.getId()).orElse(null);
                CareContextDocument document = assembler.assemble(encounter, individual,
                        link == null ? null : AbdmFlows.hyphenatedAbhaNumber(link.getAbhaNumber()),
                        link == null ? null : AbdmFlows.withSuffix(link.getAbhaAddress(), cfg.getAbhaAddressSuffix()),
                        cfg.getHipId(), cfg.getHipName());
                return new Prepared(reference, AbdmJson.write(builder.build(document)), null);
            });
        } catch (RuntimeException e) {
            logger.warn("ABDM HIP could not prepare care context {}: {}", reference, e.getClass().getSimpleName());
            return new Prepared(reference, null, "Could not prepare the record");
        }
    }

    private void notifyGateway(String consentId, String transactionId, String sessionStatus, ArrayNode statusResponses) {
        ObjectNode body = AbdmJson.obj();
        ObjectNode n = body.putObject("notification");
        n.put("consentId", consentId);
        n.put("transactionId", transactionId);
        n.put("doneAt", AbdmTime.now());
        ObjectNode notifier = n.putObject("notifier");
        notifier.put("type", "HIP");
        notifier.put("id", cfg.getHipId());
        ObjectNode status = n.putObject("statusNotification");
        status.put("sessionStatus", sessionStatus);
        status.put("hipId", cfg.getHipId());
        status.set("statusResponses", statusResponses);
        try {
            gateway.post(AbdmPaths.OUT_HI_NOTIFY, AbdmGatewayClient.newRequestId(), body, Map.of(AbdmPaths.H_HIP_ID, cfg.getHipId()));
        } catch (RuntimeException e) {
            logger.warn("ABDM HIP health information notify for {} failed: {}", transactionId, e.getMessage());
        }
    }

    private static String patientReferenceOf(JsonNode consentDetail, String careContextReference) {
        JsonNode list = AbdmJson.at(consentDetail, "careContexts");
        if (list == null || !list.isArray()) return null;
        for (JsonNode c : list) {
            if (careContextReference.equals(AbdmJson.text(c, "careContextReference"))) return AbdmJson.text(c, "patientReference");
        }
        return null;
    }
}
