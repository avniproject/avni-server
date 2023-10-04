package org.avni.server.util;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtil {
    public static String readJsonFileFromClasspath(String file) throws IOException {
        return ObjectMapperSingleton.getObjectMapper().readTree(FileUtil.class.getResource(file)).toString();
    }

    public static String readStringOfFileOnFileSystem(String file) throws IOException {
        if (!StringUtils.hasLength(file)) return null;

        byte[] encoded = Files.readAllBytes(Paths.get(file));
        return new String(encoded, StandardCharsets.UTF_8);
    }
}
