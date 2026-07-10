package org.avni.server.service;

import org.avni.server.dao.DownloadableContentRepository;
import org.avni.server.domain.DownloadableContent;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.util.EntityUtil;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.DownloadableContentRequest;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class DownloadableContentService implements NonScopeAwareService {

    private final DownloadableContentRepository downloadableContentRepository;

    @Autowired
    public DownloadableContentService(DownloadableContentRepository downloadableContentRepository) {
        this.downloadableContentRepository = downloadableContentRepository;
    }

    public DownloadableContent createOrUpdate(DownloadableContentRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (!StringUtils.hasText(name)) {
            throw new BadRequestError("Name is required");
        }
        String category = request.getCategory() == null ? "" : request.getCategory().trim();
        if (!StringUtils.hasText(category)) {
            throw new BadRequestError("Category is required");
        }

        DownloadableContent content = StringUtils.hasText(request.getUuid())
                ? downloadableContentRepository.findByUuid(request.getUuid())
                : null;
        if (content == null) {
            content = new DownloadableContent();
            if (StringUtils.hasText(request.getUuid())) {
                content.setUuid(request.getUuid());
            } else {
                content.assignUUID();
            }
        }
        assertNameIsUnique(name, content);

        content.setName(name);
        content.setCategory(category);
        content.setContentKey(StringUtils.hasText(request.getContentKey()) ? request.getContentKey().trim() : null);
        content.setSha256(StringUtils.hasText(request.getSha256()) ? request.getSha256().trim() : null);
        content.setNeedsKey(request.isNeedsKey());
        content.setPayload(toPayload(request.getPayload()));
        content.setVoided(request.isVoided());
        return downloadableContentRepository.save(content);
    }

    public void deleteContent(String uuid) {
        DownloadableContent content = loadByUuid(uuid);
        content.setVoided(true);
        content.setName(EntityUtil.getVoidedName(content.getName(), content.getId()));
        downloadableContentRepository.save(content);
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return downloadableContentRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    private JsonObject toPayload(Map<String, Object> requested) {
        if (requested == null || requested.isEmpty()) {
            return null;
        }
        return new JsonObject(requested);
    }

    private DownloadableContent loadByUuid(String uuid) {
        DownloadableContent content = downloadableContentRepository.findByUuid(uuid);
        if (content == null) {
            throw new BadRequestError("DownloadableContent with uuid '%s' not found", uuid);
        }
        return content;
    }

    private void assertNameIsUnique(String name, DownloadableContent current) {
        DownloadableContent existing = downloadableContentRepository.findByNameIgnoreCaseAndIsVoidedFalse(name);
        if (existing != null && !existing.getUuid().equals(current.getUuid())) {
            throw new BadRequestError("Downloadable content with name '%s' already exists", name);
        }
    }
}
