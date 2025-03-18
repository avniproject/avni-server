package org.avni.server.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;

public class LogUtil {
    public static void safeLogError(Logger logger, Object obj) {
        try {
            logger.error(ObjectMapperSingleton.getObjectMapper().writeValueAsString(obj));
        } catch (JsonProcessingException jsonProcessingException) {
            logger.error("Error serializing object to JSON", jsonProcessingException);
        }
    }
}
