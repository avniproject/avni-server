package org.avni.server.importer.batch.model;

import org.avni.server.domain.OrganisationConfig;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BundleZip extends HashMap<String, byte[]> {
    private static final String STRING_FOLDER_PATH_SEPARATOR = "/";
    private static final List<String> INVALID_FILE_NAME_PATTERNS = Arrays.asList("__MACOSX", ".DS_Store");

    public BundleZip(Map<? extends String, ? extends byte[]> m) {
        super(m);
    }

    public static boolean fileNameHasExclusionPatterns(String fileName) {
        return INVALID_FILE_NAME_PATTERNS.stream().anyMatch(fileName::contains);
    }

    public byte[] getFile(String fileName) {
        String matchingKey = this.keySet().stream().filter(x -> x.endsWith(fileName) && !fileNameHasExclusionPatterns(x)).findAny().orElse(null);
        return this.get(matchingKey);
    }

    public Map<String, byte[]> getFileNameAndDataInFolder(String folder) {
        Map<String, byte[]> map = new HashMap<>();
        this.entrySet().stream().filter(x -> x.getKey().contains(folder + STRING_FOLDER_PATH_SEPARATOR))
                .forEach(x -> map.put(x.getKey().substring(x.getKey().lastIndexOf(STRING_FOLDER_PATH_SEPARATOR) + 1),
                        x.getValue()));
        return map;
    }

    public List<String> getExtensionNames() {
        return this.keySet().stream().filter(bytes -> bytes.contains(String.format("%s/", OrganisationConfig.Extension.EXTENSION_DIR)) && !fileNameHasExclusionPatterns(bytes))
                .map(key -> key.substring(key.indexOf(OrganisationConfig.Extension.EXTENSION_DIR))).collect(Collectors.toList());
    }
}
