package org.avni.server.importer.batch.model;

import java.util.Arrays;
import java.util.Optional;

public enum BundleFolder {
    FORMS("forms", "form"),
    TRANSLATIONS("translations", "translation"),
    OLD_RULES("oldRules", "oldRule"),
    SUBJECT_TYPE_ICONS("subjectTypeIcons", "subjectTypeIcon"),
    REPORT_CARD_ICONS("reportCardIcons", "reportCardIcon");

    String folderName;
    String modifiedFileName;

    BundleFolder(String folderName, String modifiedFileName) {
        this.folderName = folderName;
        this.modifiedFileName = modifiedFileName;
    }

    public String getFolderName() {
        return folderName;
    }

    public String getModifiedFileName() {
        return modifiedFileName;
    }

    public static Optional<BundleFolder> getFromFileName(String fileName) {
        return Arrays.stream(BundleFolder.values())
                .filter(bundleFolder -> bundleFolder.getFolderName().equals(fileName))
                .findFirst();
    }
}
