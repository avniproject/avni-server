package org.avni.server.web;

import org.avni.server.service.abha.AbhaEventInvalidException;
import org.avni.server.service.abha.AbhaEventRequest;
import org.avni.server.service.abha.AbhaEventService;
import org.avni.server.service.abha.AbhaRefusal;
import org.avni.server.service.abha.AbhaRefusedException;
import org.avni.server.service.abha.AbhaService;
import org.avni.server.service.abha.AbhaSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    public AbhaController(AbhaService abhaService) {
        this.abhaService = abhaService;
    }

    @GetMapping("/abha/session")
    @PreAuthorize("hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public AbhaSession getSession(@RequestParam(value = "subjectTypeUUID", required = false) String subjectTypeUUID) {
        return abhaService.createSession(subjectTypeUUID);
    }

    // 201 when stored, 200 when this uuid was already stored.
    @PostMapping("/abha/events")
    @PreAuthorize("hasAnyAuthority('user')")
    @Transactional
    public ResponseEntity<Void> recordEvent(@RequestBody AbhaEventRequest request) {
        return ResponseEntity.status(abhaEventService.recordEvent(request) ? HttpStatus.CREATED : HttpStatus.OK).build();
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
            case ABHA_UNKNOWN_SUBJECT_TYPE -> HttpStatus.BAD_REQUEST;
            case ABHA_DISABLED, ABHA_NO_PRIVILEGE, ABHA_NO_HEALTH_CENTRE -> HttpStatus.FORBIDDEN;
            case ABHA_NOT_CONFIGURED -> HttpStatus.CONFLICT;
            case ABHA_EKA_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
        };
    }
}
