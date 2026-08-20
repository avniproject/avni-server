package org.avni.server.domain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.avni.server.domain.MediaFolder.Retention.METADATA;
import static org.avni.server.domain.MediaFolder.Retention.TRANSACTIONAL;

// Registry of every subfolder under an organisation's media directory. Register new subfolders here, not as loose string literals.
public enum MediaFolder {
    NEWS("news", TRANSACTIONAL, Content.IMAGE),
    PROFILE_PICS("profile-pics", TRANSACTIONAL, Content.IMAGE),
    ICONS("icons", METADATA, Content.IMAGE),
    MetaData("metadata", METADATA, Content.IMAGE),
    CUSTOM_CARD_CONFIGS("customcardconfigs", METADATA, Content.NON_IMAGE),
    FORM_SHARE_TEMPLATES("formsharetemplates", METADATA, Content.NON_IMAGE),
    EXTENSIONS("extensions", METADATA, Content.NON_IMAGE);

    public enum Retention {
        METADATA,
        TRANSACTIONAL
    }

    public enum Content {
        IMAGE,
        NON_IMAGE
    }

    public final String label;
    public final Retention retention;
    public final Content content;

    MediaFolder(String label, Retention retention, Content content) {
        this.label = label;
        this.retention = retention;
        this.content = content;
    }

    public static MediaFolder valueOfLabel(String label) {
        for (MediaFolder folderName : values()) {
            if (folderName.label.equals(label)) {
                return folderName;
            }
        }
        return null;
    }

    public static List<MediaFolder> getFoldersWithTransactionalData() {
        return Arrays.stream(values())
                .filter(folder -> folder.retention == TRANSACTIONAL)
                .collect(Collectors.toList());
    }

    public static MediaFolder imageFolderOfLabel(String label) {
        MediaFolder folder = valueOfLabel(label);
        return folder != null && folder.content == Content.IMAGE ? folder : null;
    }
}
