package org.avni.server.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MediaFolderTest {

    @Test
    void onlyNewsAndProfilePicsHoldTransactionalData() {
        List<MediaFolder> folders = MediaFolder.getFoldersWithTransactionalData();
        assertEquals(Arrays.asList(MediaFolder.NEWS, MediaFolder.PROFILE_PICS), folders);
    }

    @Test
    void imageFolderOfLabelResolvesOnlyImageFolders() {
        assertSame(MediaFolder.ICONS, MediaFolder.imageFolderOfLabel("icons"));
        assertSame(MediaFolder.MetaData, MediaFolder.imageFolderOfLabel("metadata"));
        assertSame(MediaFolder.NEWS, MediaFolder.imageFolderOfLabel("news"));
        assertSame(MediaFolder.PROFILE_PICS, MediaFolder.imageFolderOfLabel("profile-pics"));
        assertNull(MediaFolder.imageFolderOfLabel("customcardconfigs"));
        assertNull(MediaFolder.imageFolderOfLabel("formsharetemplates"));
        assertNull(MediaFolder.imageFolderOfLabel("extensions"));
        assertNull(MediaFolder.imageFolderOfLabel("unknown"));
    }
}
