package org.avni.server.domain;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

// Reserved key prefixes for admin-managed content, each content-addressed by sha256 and carrying
// its own storage class so the two route independently (see StorageResolver).
public enum ManagedContentNamespace {
    MODELS(StorageDataClass.MODEL_NAMESPACE, "^[0-9a-f]{64}\\.bin$", "<sha256>.bin", StorageDataClass.MODEL),
    GUIDANCE(StorageDataClass.GUIDANCE_NAMESPACE, "^[0-9a-f]{64}\\.(png|jpg|jpeg)$", "<sha256>.png|.jpg|.jpeg", StorageDataClass.GUIDANCE);

    private final String prefix;
    private final Pattern fileNamePattern;
    private final String expectedFileNameForm;
    private final StorageDataClass dataClass;

    ManagedContentNamespace(String prefix, String fileNamePattern, String expectedFileNameForm, StorageDataClass dataClass) {
        this.prefix = prefix;
        this.fileNamePattern = Pattern.compile(fileNamePattern);
        this.expectedFileNameForm = expectedFileNameForm;
        this.dataClass = dataClass;
    }

    public static Optional<ManagedContentNamespace> forPrefix(String prefix) {
        return Arrays.stream(values()).filter(ns -> ns.prefix.equals(prefix)).findFirst();
    }

    public static Optional<ManagedContentNamespace> forRelativeKey(String relativeKey) {
        if (relativeKey == null) {
            return Optional.empty();
        }
        int separator = relativeKey.indexOf('/');
        if (separator < 0) {
            return Optional.empty();
        }
        return forPrefix(relativeKey.substring(0, separator))
                .filter(ns -> ns.accepts(relativeKey.substring(separator + 1)));
    }

    public boolean accepts(String fileName) {
        return fileName != null && fileNamePattern.matcher(fileName).matches();
    }

    public boolean relativeKeyStartsWithHash(String relativeKey, String sha256) {
        return relativeKey.startsWith(String.format("%s/%s.", prefix, sha256));
    }

    public static String expectedKeyForms() {
        return Arrays.stream(values())
                .map(ns -> String.format("%s/%s", ns.prefix, ns.expectedFileNameForm))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    public String relativeKeyFor(String fileName) {
        return String.format("%s/%s", prefix, fileName);
    }

    public String getPrefix() {
        return prefix;
    }

    public String getExpectedFileNameForm() {
        return expectedFileNameForm;
    }

    public StorageDataClass getDataClass() {
        return dataClass;
    }
}
