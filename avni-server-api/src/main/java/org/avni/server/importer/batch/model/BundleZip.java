package org.avni.server.importer.batch.model;

import org.avni.server.domain.OrganisationConfig;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BundleZip extends HashMap<String, byte[]> {
    public BundleZip(Map<? extends String, ? extends byte[]> m) {
        super(m);
    }

    public byte[] getFile(String fileName) {
        String matchingKey = this.keySet().stream().filter(x -> x.endsWith(fileName)).findAny().orElse(null);
        return this.get(matchingKey);
    }

    public List<String> getForms() {
        return this.entrySet().stream().filter(x -> x.getKey().contains("forms/"))
                .map(x -> new String(x.getValue(), StandardCharsets.UTF_8)).collect(Collectors.toList());
    }

    public List<String> getExtensionNames() {
        return this.keySet().stream().filter(bytes -> bytes.contains(String.format("%s/", OrganisationConfig.Extension.EXTENSION_DIR)))
                .map(key -> key.substring(key.indexOf(OrganisationConfig.Extension.EXTENSION_DIR))).collect(Collectors.toList());
    }
}
