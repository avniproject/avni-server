package org.avni.server.dao;

import org.avni.server.domain.DocumentationItem;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentationItemRepository extends ReferenceDataRepository<DocumentationItem>, FindByLastModifiedDateTime<DocumentationItem> {

    default DocumentationItem findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in DocumentationItem.");
    }

    default DocumentationItem findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in DocumentationItem.");
    }

}
