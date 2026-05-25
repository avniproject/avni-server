package org.avni.server.service;

import org.avni.server.framework.security.UserContextHolder;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ExtensionService implements NonScopeAwareService {

    private final S3Service s3Service;

    @Autowired
    public ExtensionService(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        // No S3 directory configured for the org → no extension files to sync. Skip the lookup
        // instead of letting StorageService.getOrgDirectoryName throw IllegalStateException.
        if (UserContextHolder.getUserContext().getOrganisation().getMediaDirectory() == null) {
            return false;
        }
        return s3Service.listExtensionFiles(java.util.Optional.ofNullable(lastModifiedDateTime)).size() > 0;
    }
}
