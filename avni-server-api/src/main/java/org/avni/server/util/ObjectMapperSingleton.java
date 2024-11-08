package org.avni.server.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;

public final class ObjectMapperSingleton {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JodaModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private ObjectMapperSingleton() {
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
