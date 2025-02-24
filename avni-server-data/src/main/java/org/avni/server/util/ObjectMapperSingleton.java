package org.avni.server.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

public final class ObjectMapperSingleton {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JodaModule())
            .registerModule(new Jdk8Module()) //newly added
            .registerModule(new JavaTimeModule()) //newly added
            .registerModule(new ParameterNamesModule()) //newly added
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private ObjectMapperSingleton() {
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
