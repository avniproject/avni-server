package org.avni.server.util;

import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class WebResponseUtil {
    public static ResponseEntity<Map<String, String>> createBadRequestResponse(Exception e, Logger logger) {
        logger.error(e.getMessage());
        return ResponseEntity.badRequest().body(generateJsonError(e.getMessage()));
    }

    public static ResponseEntity<Map<String, String>> createInternalServerErrorResponse(Exception e, Logger logger) {
        logger.error(e.getMessage(), e);
        return ResponseEntity.badRequest().body(generateJsonError(e.getMessage()));
    }

    public static Map<String, String> generateJsonError(String errorMsg) {
        Map<String, String> errorMap = new HashMap<>();
        errorMap.put("message", errorMsg);
        return errorMap;
    }
}
