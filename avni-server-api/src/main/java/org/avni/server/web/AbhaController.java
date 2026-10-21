package org.avni.server.web;

import org.avni.server.service.abha.AbhaCardService;
import org.avni.server.service.abha.AbhaEventInvalidException;
import org.avni.server.service.abha.AbhaEventRequest;
import org.avni.server.service.abha.AbhaEventService;
import org.avni.server.service.abha.AbhaRefusal;
import org.avni.server.service.abha.AbhaRefusedException;
import org.avni.server.service.abha.AbhaService;
import org.avni.server.service.abha.AbhaSession;
import org.avni.server.web.request.AbhaSubjectSearchRequest;
import org.avni.server.web.response.AbhaSubjectSearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

// Called by the Android app, online, when the ABHA screen opens; outside sync, like MediaController's /media/signedUrl.
@RestController
public class AbhaController {
    private final AbhaService abhaService;
    @Autowired
    private AbhaEventService abhaEventService;
    @Autowired
    private AbhaCardService abhaCardService;

    @Autowired
    public AbhaController(AbhaService abhaService) {
        this.abhaService = abhaService;
    }

    @GetMapping("/abha/session")
    @PreAuthorize("hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public AbhaSession getSession(@RequestParam(value = "subjectTypeUUID", required = false) String subjectTypeUUID) {
        return abhaService.createSession(subjectTypeUUID);
    }

    // POST, though it only reads: the number travels in the body, because a URL lands in the app's request log and the
    // server's access log, and an ABHA number is a health identifier.
    @PostMapping("/abha/subjects/search")
    @PreAuthorize("hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public AbhaSubjectSearchResponse searchSubjects(@RequestBody AbhaSubjectSearchRequest request) {
        return new AbhaSubjectSearchResponse(abhaService.findSubjectUUIDs(request.getSubjectTypeUUID(), request.getAbhaNumber()));
    }

    // 201 when stored, 200 when this uuid was already stored.
    @PostMapping("/abha/events")
    @PreAuthorize("hasAnyAuthority('user')")
    @Transactional
    public ResponseEntity<Void> recordEvent(@RequestBody AbhaEventRequest request) {
        return ResponseEntity.status(abhaEventService.recordEvent(request) ? HttpStatus.CREATED : HttpStatus.OK).build();
    }

    // The patient's ABHA card as a PNG, fetched from Eka for this request. The phone shows it and deletes it; no-store
    // keeps every cache between from holding a picture of the patient's identity. The DB connection is held through
    // Eka's answer (at most 10 s, 20 s when the organisation's token is renewed too): open-in-view keeps a request's
    // connection to its end, as /abha/session does through its login.
    @GetMapping(value = "/abha/card", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> getCard(@RequestParam("subjectUUID") String subjectUUID) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(abhaCardService.getCard(subjectUUID));
    }

    @ExceptionHandler(AbhaEventInvalidException.class)
    public ResponseEntity<Map<String, String>> invalidEvent(AbhaEventInvalidException e) {
        return ResponseEntity.badRequest().body(Collections.singletonMap("code", "ABHA_EVENT_INVALID"));
    }

    @ExceptionHandler(AbhaRefusedException.class)
    public ResponseEntity<Map<String, String>> refused(AbhaRefusedException e) {
        return ResponseEntity.status(statusOf(e.getRefusal())).body(Collections.singletonMap("code", e.getRefusal().name()));
    }

    // No default: a new refusal does not compile until it is given a status.
    private static HttpStatus statusOf(AbhaRefusal refusal) {
        return switch (refusal) {
            case ABHA_UNKNOWN_SUBJECT_TYPE, ABHA_NUMBER_INVALID -> HttpStatus.BAD_REQUEST;
            case ABHA_DISABLED, ABHA_NO_PRIVILEGE, ABHA_NO_HEALTH_CENTRE, ABHA_NOT_YOURS -> HttpStatus.FORBIDDEN;
            case ABHA_NO_CARD -> HttpStatus.NOT_FOUND;
            case ABHA_NOT_CONFIGURED, ABHA_SESSION_EXPIRED -> HttpStatus.CONFLICT;
            case ABHA_EKA_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
        };
    }
}
