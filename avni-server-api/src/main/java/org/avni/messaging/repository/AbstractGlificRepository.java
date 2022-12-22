package org.avni.messaging.repository;

import org.avni.server.util.ObjectMapperSingleton;

import java.io.IOException;

public abstract class AbstractGlificRepository {
    protected String getJson(String fileNameWithoutExtension) {
        try {
            return ObjectMapperSingleton.getObjectMapper().readTree(this.getClass().getResource(String.format("/external/glific/%s.json", fileNameWithoutExtension))).toString();
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
    }
}
