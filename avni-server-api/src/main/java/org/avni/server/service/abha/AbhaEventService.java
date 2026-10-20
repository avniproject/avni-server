package org.avni.server.service.abha;

import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.UserSettings;
import org.avni.server.framework.security.UserContextHolder;
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

// The audit record of every ABHA action. The record is the audit, so nothing from it is logged but its uuid and action.
@Service
public class AbhaEventService {
    private static final Logger logger = LoggerFactory.getLogger(AbhaEventService.class);
    public static final String CREATED = "created";
    public static final String LINKED = "linked";
    public static final String REMOVED = "removed";
    public static final String UNKNOWN_ROUTE = "unknown";
    private static final List<String> PHONE_ACTIONS = Arrays.asList(CREATED, LINKED);
    private static final List<String> ROUTES = Arrays.asList("aadhaar", "mobile", "abha_number", "abha_address", UNKNOWN_ROUTE);

    private final AbhaService abhaService;
    private final AbhaEventRepository abhaEventRepository;

    @Autowired
    public AbhaEventService(AbhaService abhaService, AbhaEventRepository abhaEventRepository) {
        this.abhaService = abhaService;
        this.abhaEventRepository = abhaEventRepository;
    }

    // True when stored now, false when this uuid was already stored.
    public boolean recordEvent(AbhaEventRequest request) {
        AbhaAccess access = abhaService.checkAccess(request.getSubjectTypeUUID());
        if (!PHONE_ACTIONS.contains(request.getAction())) {
            throw new AbhaEventInvalidException("action must be created or linked");
        }
        if (!StringUtils.hasText(request.getUuid()) || !StringUtils.hasText(request.getSubjectUUID())) {
            throw new AbhaEventInvalidException("uuid and subjectUUID are required");
        }
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

    // Written by the server, not the phone, when a sync removes a subject's ABHA or voids the subject. It names the user
    // whose save carried the change; no health centre is checked, so an admin's upload without one still leaves the record.
    public void recordRemoved(String subjectUuid) {
        UserContext userContext = UserContextHolder.getUserContext();
        User user = userContext.getUser();
        String hipId = user.getSettings() == null ? null : user.getSettings().getString(UserSettings.ABHA_HIP_ID);
        AbhaEvent event = new AbhaEvent();
        event.assignUUID();
        event.setOrganisationId(userContext.getOrganisationId());
        event.setSubjectUuid(subjectUuid);
        event.setUserId(user.getId());
        event.setHipId(hipId == null ? "" : hipId);
        event.setAction(REMOVED);
        event.setRoute(UNKNOWN_ROUTE);
        event.setOccurredAt(Instant.now());
        abhaEventRepository.save(event);
        logger.info("ABHA event {} recorded, action {}", event.getUuid(), event.getAction());
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
