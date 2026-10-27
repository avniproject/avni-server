package org.avni.server.service.abha;

import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.domain.Individual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// The audit record of every ABHA action. The record is the audit, so nothing from it is logged but its uuid and action.
@Service
public class AbhaEventService {
    private static final Logger logger = LoggerFactory.getLogger(AbhaEventService.class);
    public static final String CREATED = "created";
    public static final String LINKED = "linked";
    public static final String REMOVED = "removed";

    // The record, among a patient's records newest first, of the ABHA their saved record holds: the latest since the
    // last removal whose ABHA number (else, with no number on either, address) is the saved one's, with an Eka ID.
    // Never a later lookup of another ABHA a worker left unsaved (27 Oct run: the card, the link and the consent
    // request all took the latest lookup for the patient's own).
    public static Optional<AbhaEvent> ofSavedAbha(List<AbhaEvent> newestFirst, String savedNumber, String savedAddress) {
        String number = AbhaLinkService.digitsOnly(savedNumber);
        String name = addressName(savedAddress);
        if (number.length() != AbhaLinkService.ABHA_NUMBER_DIGITS) number = "";
        if (number.isEmpty() && name == null) return Optional.empty();
        for (AbhaEvent record : newestFirst) {
            if (REMOVED.equals(record.getAction())) return Optional.empty();
            if (!StringUtils.hasText(record.getEkaOid())) continue;
            String recordNumber = AbhaLinkService.digitsOnly(record.getAbhaNumber());
            boolean same = !number.isEmpty() && !recordNumber.isEmpty() ? number.equals(recordNumber)
                    : name != null && name.equals(addressName(record.getAbhaAddress()));
            if (same) return Optional.of(record);
        }
        return Optional.empty();
    }

    private static String addressName(String address) {
        if (!StringUtils.hasText(address)) return null;
        return address.trim().split("@", 2)[0].toLowerCase(java.util.Locale.ROOT);
    }
    // The ABHA verified again from its card, once the patient's login at Eka had lapsed.
    public static final String VERIFIED = "verified";
    public static final String UNKNOWN_ROUTE = "unknown";
    private static final List<String> PHONE_ACTIONS = Arrays.asList(CREATED, LINKED, VERIFIED);
    private static final List<String> ROUTES = Arrays.asList("aadhaar", "mobile", "abha_number", "abha_address", UNKNOWN_ROUTE);

    private final AbhaService abhaService;
    private final AbhaEventRepository abhaEventRepository;
    private final AbhaRemovedEventWriter abhaRemovedEventWriter;
    private final IndividualRepository individualRepository;

    @Autowired
    public AbhaEventService(AbhaService abhaService, AbhaEventRepository abhaEventRepository, AbhaRemovedEventWriter abhaRemovedEventWriter,
                            IndividualRepository individualRepository) {
        this.abhaService = abhaService;
        this.abhaEventRepository = abhaEventRepository;
        this.abhaRemovedEventWriter = abhaRemovedEventWriter;
        this.individualRepository = individualRepository;
    }

    // True when stored now, false when this uuid was already stored.
    public boolean recordEvent(AbhaEventRequest request) {
        AbhaAccess access = abhaService.checkAccess(request.getSubjectTypeUUID());
        if (!PHONE_ACTIONS.contains(request.getAction())) {
            throw new AbhaEventInvalidException("action must be created, linked or verified");
        }
        if (!StringUtils.hasText(request.getUuid()) || !StringUtils.hasText(request.getSubjectUUID())) {
            throw new AbhaEventInvalidException("uuid and subjectUUID are required");
        }
        checkSubjectOnServer(request);
        if (abhaEventRepository.findByUuid(request.getUuid()) != null) {
            return false;
        }
        AbhaEvent event = new AbhaEvent();
        event.setUuid(request.getUuid());
        event.setOrganisationId(access.getOrganisationId());
        event.setSubjectUuid(request.getSubjectUUID());
        event.setUserId(access.getUser().getId());
        event.setHipId(access.getHipId());
        event.setAction(request.getAction());
        // A route this server does not know, or none, is kept as unknown: the record is worth more than the label.
        event.setRoute(ROUTES.contains(request.getRoute()) ? request.getRoute() : UNKNOWN_ROUTE);
        event.setAbhaNumber(digitsOf(request.getAbhaNumber()));
        event.setAbhaAddress(request.getAbhaAddress());
        event.setEkaOid(request.getEkaOid());
        event.setEkaTxnId(request.getEkaTxnId());
        event.setConsentGivenAt(instantOf(request.getConsentGivenAt(), "consentGivenAt", null));
        event.setOccurredAt(instantOf(request.getOccurredAt(), "occurredAt", Instant.now()));
        abhaEventRepository.save(event);
        logger.info("ABHA event {} recorded, action {}", event.getUuid(), event.getAction());
        return true;
    }

    // The latest record names the Eka ID whose card everyone who may see the patient is served. So a patient already on
    // the server must be of the subject type the user's privilege was checked on, and within what their phone may hold;
    // otherwise a worker could put any Eka ID on a patient they cannot see. A patient the server does not have yet, a
    // registration that has not synced, is accepted: its card is served only from the user's own records.
    private void checkSubjectOnServer(AbhaEventRequest request) {
        Individual individual = individualRepository.findByUuid(request.getSubjectUUID());
        if (individual == null) {
            return;
        }
        if (!individual.getSubjectType().getUuid().equals(request.getSubjectTypeUUID())) {
            throw new AbhaRefusedException(AbhaRefusal.ABHA_NO_PRIVILEGE);
        }
        abhaService.checkSubjectWithinReach(individual);
    }

    // Written by the server, not the phone, when a sync removes a subject's ABHA or voids the subject. It names the user
    // whose save carried the change; no health centre is checked, so an admin's upload without one still leaves the record.
    public void recordRemoved(String subjectUuid) {
        abhaRemovedEventWriter.recordRemoved(subjectUuid);
    }

    private static String digitsOf(String abhaNumber) {
        if (!StringUtils.hasText(abhaNumber)) {
            return null;
        }
        String digits = abhaNumber.replaceAll("\\D", "");
        if (digits.length() > 14) {
            throw new AbhaEventInvalidException("abhaNumber has more than 14 digits");
        }
        return digits.isEmpty() ? null : digits;
    }

    private static Instant instantOf(String value, String field, Instant whenMissing) {
        if (!StringUtils.hasText(value)) {
            return whenMissing;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new AbhaEventInvalidException(field + " is not an ISO-8601 time");
        }
    }
}
