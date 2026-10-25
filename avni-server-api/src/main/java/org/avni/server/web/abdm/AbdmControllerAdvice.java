package org.avni.server.web.abdm;

import org.avni.server.service.abdm.gateway.AbdmGatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

// DEMO-ONLY abdm-hackathon: never merged.
// ABDM controllers only. Ordered first, because the global ErrorInterceptors handles every exception type and Spring
// takes the first advice with any matching handler, not the closest match.
@RestControllerAdvice(basePackages = "org.avni.server.web.abdm")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AbdmControllerAdvice {
    private static final Logger logger = LoggerFactory.getLogger(AbdmControllerAdvice.class);

    @ExceptionHandler(AbdmGatewayException.class)
    public ResponseEntity<Map<String, Object>> gateway(AbdmGatewayException e) {
        logger.warn("ABDM gateway failure: HTTP {} {}", e.getStatus(), e.getAbdmCode());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "ABDM_GATEWAY");
        body.put("status", e.getStatus());
        body.put("abdmCode", e.getAbdmCode());
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "ABDM_BAD_REQUEST");
        body.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(body);
    }
}
