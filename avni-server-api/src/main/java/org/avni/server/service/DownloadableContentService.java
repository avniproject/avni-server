package org.avni.server.service;

import org.avni.server.dao.DownloadableContentRepository;
import org.avni.server.domain.DownloadableContent;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.ManagedContentNamespace;
import org.avni.server.domain.util.EntityUtil;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.DownloadableContentRequest;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DownloadableContentService implements NonScopeAwareService {
    private static final String GUIDANCE_IMAGE_CATEGORY = "guidanceImage";
    private static final String GUIDANCE_SEQUENCE = "sequence";
    private static final String GUIDANCE_SITE = "site";
    private static final String GUIDANCE_KIND = "kind";
    private static final List<String> GUIDANCE_KINDS = List.of("reckoner", "overlay");

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
        JsonObject payload = toPayload(request.getPayload());
        if (!request.isVoided() && GUIDANCE_IMAGE_CATEGORY.equals(category)) {
            assertGuidanceDetailsAreValid(payload);
            assertGuidancePositionIsFree(payload, content);
        }

        content.setName(name);
        content.setCategory(category);
        String contentKey = StringUtils.hasText(request.getContentKey()) ? request.getContentKey().trim() : null;
        String sha256 = StringUtils.hasText(request.getSha256()) ? request.getSha256().trim() : null;
        assertContentKeyIsAddressable(contentKey, sha256);
        content.setContentKey(contentKey);
        content.setSha256(sha256);
        content.setNeedsKey(request.isNeedsKey());
        content.setPayload(payload);
        content.setVoided(request.isVoided());
        return downloadableContentRepository.save(content);
    }

    // The device resolves the path from contentKey and hashes that file against sha256, so a key
    // naming a different hash can never download, on any sync.
    private void assertContentKeyIsAddressable(String contentKey, String sha256) {
        if (contentKey == null) {
            return;
        }
        int separator = contentKey.indexOf('/');
        String prefix = separator < 0 ? "" : contentKey.substring(0, separator);
        ManagedContentNamespace namespace = ManagedContentNamespace.forPrefix(prefix)
                .orElseThrow(() -> new BadRequestError("Invalid contentKey '%s'. Expected one of %s.",
                        contentKey, ManagedContentNamespace.expectedKeyForms()));
        if (!namespace.addresses(contentKey, sha256)) {
            throw new BadRequestError("contentKey '%s' must be named after sha256 '%s'.", contentKey, sha256);
        }
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

    // The phone finds a guidance picture by an exact match on position and picture type, and labels the photo row with
    // the site, so details the admin screen would refuse save a picture no rule can find. They slip past the duplicate
    // check below as well: a position sent as the text "91" never equals the number 91 (avniproject/avni-webapp#1798).
    // Worded as the screen words the same checks.
    private void assertGuidanceDetailsAreValid(JsonObject payload) {
        JsonObject details = payload == null ? new JsonObject() : payload;
        List<String> problems = new ArrayList<>();
        if (!isWholeNumberFromOne(details.get(GUIDANCE_SEQUENCE))) {
            problems.add("position (a whole number, 1 or more)");
        }
        Object site = details.get(GUIDANCE_SITE);
        if (!(site instanceof String) || !StringUtils.hasText((String) site)) {
            problems.add("site name (a non-empty string)");
        }
        Object kind = details.get(GUIDANCE_KIND);
        if (!(kind instanceof String) || !GUIDANCE_KINDS.contains(kind)) {
            problems.add("picture type (Reference photo or Framing outline)");
        }
        if (!problems.isEmpty()) {
            throw new BadRequestError("Guidance details are missing or invalid: %s.", String.join(", ", problems));
        }
    }

    // JSON brings a number in as an Integer, a Long or a Double depending on how it was written, and 91.0 is the same
    // position as 91 to the phone.
    private static boolean isWholeNumberFromOne(Object value) {
        if (!(value instanceof Number)) {
            return false;
        }
        double number = ((Number) value).doubleValue();
        return number >= 1 && !Double.isInfinite(number) && number == Math.rint(number);
    }

    // A rule finds a guidance picture by its position and picture type, and the phone takes the first match, so a
    // second record for the same pair could show a stale picture in its place (avniproject/avni-webapp#1798). The
    // admin screen checks this too, but only against the records it last fetched.
    private void assertGuidancePositionIsFree(JsonObject payload, DownloadableContent current) {
        Number sequence = (Number) payload.get(GUIDANCE_SEQUENCE);
        Object kind = payload.get(GUIDANCE_KIND);
        downloadableContentRepository.findAllByCategoryAndIsVoidedFalse(GUIDANCE_IMAGE_CATEGORY).stream()
                .filter(existing -> !existing.getUuid().equals(current.getUuid()))
                .filter(existing -> existing.getPayload() != null
                        && samePosition(existing.getPayload().get(GUIDANCE_SEQUENCE), sequence)
                        && kind.equals(existing.getPayload().get(GUIDANCE_KIND)))
                .findFirst()
                .ifPresent(existing -> {
                    throw new BadRequestError("Guidance picture \"%s\" already has position %s and this picture type. Edit that record instead.",
                            existing.getName(), sequence);
                });
    }

    // A stored payload and a request can carry the same whole number as different Number types.
    private static boolean samePosition(Object stored, Number requested) {
        return stored instanceof Number && ((Number) stored).doubleValue() == requested.doubleValue();
    }

    private void assertNameIsUnique(String name, DownloadableContent current) {
        DownloadableContent existing = downloadableContentRepository.findByNameIgnoreCaseAndIsVoidedFalse(name);
        if (existing != null && !existing.getUuid().equals(current.getUuid())) {
            throw new BadRequestError("Downloadable content with name '%s' already exists", name);
        }
    }
}
