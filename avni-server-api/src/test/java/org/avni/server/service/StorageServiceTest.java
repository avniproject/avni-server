package org.avni.server.service;

import org.avni.server.domain.MediaFolder;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StorageServiceTest {
    private final Set<String> transactionalFolders = MediaFolder.getFoldersWithTransactionalData().stream()
            .map(folder -> folder.label)
            .collect(Collectors.toSet());

    @Test
    public void metadataFoldersArePreservedOnTransactionalDelete() {
        assertFalse(StorageService.holdsTransactionalData("icons/subject-type.png", transactionalFolders));
        assertFalse(StorageService.holdsTransactionalData("metadata/concept-image.jpg", transactionalFolders));
        assertFalse(StorageService.holdsTransactionalData("customcardconfigs/card.html", transactionalFolders));
        assertFalse(StorageService.holdsTransactionalData("formsharetemplates/template.html", transactionalFolders));
        assertFalse(StorageService.holdsTransactionalData("extensions/theme/style.css", transactionalFolders));
    }

    @Test
    public void transactionalFoldersAreDeletedOnTransactionalDelete() {
        assertTrue(StorageService.holdsTransactionalData("news/announcement.png", transactionalFolders));
        assertTrue(StorageService.holdsTransactionalData("profile-pics/subject.jpg", transactionalFolders));
    }

    @Test
    public void keysDirectlyUnderOrgMediaDirectoryAreDeletedOnTransactionalDelete() {
        assertTrue(StorageService.holdsTransactionalData("observation-media.jpg", transactionalFolders));
    }

    @Test
    public void unregisteredSubfoldersArePreservedOnTransactionalDelete() {
        assertFalse(StorageService.holdsTransactionalData("some-future-folder/file.html", transactionalFolders));
    }
}
