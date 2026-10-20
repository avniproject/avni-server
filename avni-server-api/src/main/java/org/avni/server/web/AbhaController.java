package org.avni.server.web;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

// Called by the Android app, online, when the ABHA screen opens; outside sync, like MediaController's /media/signedUrl.
@RestController
public class AbhaController {
    private final AbhaService abhaService;

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

    @ExceptionHandler(AbhaRefusedException.class)
    public ResponseEntity<Map<String, String>> refused(AbhaRefusedException e) {
        return ResponseEntity.status(statusOf(e.getRefusal())).body(Collections.singletonMap("code", e.getRefusal().name()));
    }

    private static HttpStatus statusOf(AbhaRefusal refusal) {
        switch (refusal) {
            case ABHA_UNKNOWN_SUBJECT_TYPE:
                return HttpStatus.BAD_REQUEST;
            case ABHA_NOT_CONFIGURED:
                return HttpStatus.CONFLICT;
            default:
                return HttpStatus.FORBIDDEN;
        }
    }
}
