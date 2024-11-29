package org.avni.server.service.media;

public enum MediaFolder {
    NEWS("news"),
    PROFILE_PICS("profile-pics"),
    ICONS("icons");
    public final String label;

    MediaFolder(String label) {
        this.label = label;
    }

    public static MediaFolder valueOfLabel(String label) {
        for (MediaFolder folderName : values()) {
            if (folderName.label.equals(label)) {
                return folderName;
            }
        }
        return null;
    }
}
