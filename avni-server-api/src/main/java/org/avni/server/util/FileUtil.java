package org.avni.server.util;

import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtil {
    public static String readJsonFileFromClasspath(String file) throws IOException {
        return ObjectMapperSingleton.getObjectMapper().readTree(FileUtil.class.getResource(file)).toString();
    }

    public static String readStringOfFileOnFileSystem(String file) throws IOException {
        byte[] bytes = FileUtil.readBytesOfFileOnFileSystem(file);
        if (bytes == null) return null;
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static byte[] readBytesOfFileOnFileSystem(String file) throws IOException {
        if (!StringUtils.hasLength(file)) return null;
        return Files.readAllBytes(Paths.get(file));
    }
}
