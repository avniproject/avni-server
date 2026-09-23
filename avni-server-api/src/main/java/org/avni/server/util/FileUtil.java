package org.avni.server.util;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtil {
    /**
     * Excel on Windows has no way to tell that a CSV is UTF-8 and falls back to the machine's ANSI
     * codepage, which renders every non-Latin script as mojibake. Leading this character tells it.
     * BatchConfiguration strips it again on the way back in, so a downloaded file stays uploadable.
     */
    public static final String UTF8_BOM = "\uFEFF";

    public static String withUtf8Bom(String content) {
        return content == null ? UTF8_BOM : UTF8_BOM.concat(stripUtf8Bom(content));
    }

    public static String stripUtf8Bom(String content) {
        // Only the leading marker. A U+FEFF further in is content, often pasted in with a concept
        // name, and deleting it silently would change what the file says.
        return content == null ? null : content.replaceFirst("^" + UTF8_BOM + "+", "");
    }

    public static String readJsonFileFromClasspath(String file) throws IOException {
        return ObjectMapperSingleton.getObjectMapper().readTree(FileUtil.class.getResource(file)).toString();
    }

    public static String readFileContentsFromClasspath(String filePath) throws IOException, URISyntaxException {
        if (!StringUtils.hasLength(filePath)) return null;

        URL resource = FileUtil.class.getResource(filePath);
        byte[] encoded = Files.readAllBytes(Paths.get(resource.toURI()));
        return new String(encoded, StandardCharsets.UTF_8);
    }

    public static String readJsonFileFromFileSystem(String file) throws IOException {
        if (!StringUtils.hasLength(file)) return null;

        byte[] encoded = Files.readAllBytes(Paths.get(file));
        return new String(encoded, StandardCharsets.UTF_8);
    }
}
