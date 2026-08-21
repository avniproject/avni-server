package org.avni.server.service;

import org.avni.server.domain.MediaFolder;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StorageServiceTest {
    private final Set<String> metadataFolders = MediaFolder.getMetadataFolders().stream()
            .map(folder -> folder.label)
            .collect(Collectors.toSet());

    @Test
    public void metadataFoldersArePreservedOnTransactionalDelete() {
        assertFalse(StorageService.holdsTransactionalData("icons/subject-type.png", metadataFolders));
        assertFalse(StorageService.holdsTransactionalData("metadata/concept-image.jpg", metadataFolders));
        assertFalse(StorageService.holdsTransactionalData("customcardconfigs/card.html", metadataFolders));
        assertFalse(StorageService.holdsTransactionalData("formsharetemplates/template.html", metadataFolders));
        assertFalse(StorageService.holdsTransactionalData("extensions/theme/style.css", metadataFolders));
    }

    @Test
    public void transactionalFoldersAreDeletedOnTransactionalDelete() {
        assertTrue(StorageService.holdsTransactionalData("news/announcement.png", metadataFolders));
        assertTrue(StorageService.holdsTransactionalData("profile-pics/subject.jpg", metadataFolders));
        assertTrue(StorageService.holdsTransactionalData("thumbnails/subject-thumb.jpg", metadataFolders));
    }

    @Test
    public void keysDirectlyUnderOrgMediaDirectoryAreDeletedOnTransactionalDelete() {
        assertTrue(StorageService.holdsTransactionalData("observation-media.jpg", metadataFolders));
    }

    @Test
    public void unregisteredSubfoldersAreDeletedOnTransactionalDelete() {
        assertTrue(StorageService.holdsTransactionalData("some-future-folder/file.html", metadataFolders));
    }
}
