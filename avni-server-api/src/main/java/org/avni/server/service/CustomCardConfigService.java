package org.avni.server.service;

import org.avni.server.dao.CardRepository;
import org.avni.server.dao.CustomCardConfigRepository;
import org.avni.server.domain.CustomCardConfig;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.util.EntityUtil;
import org.avni.server.util.AvniFiles;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.CustomCardConfigRequest;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static java.lang.String.format;

@Service
public class CustomCardConfigService implements NonScopeAwareService {
    public static final String CUSTOM_CARD_CONFIGS_SUBDIR = "customcardconfigs";
    private static final Logger logger = LoggerFactory.getLogger(CustomCardConfigService.class);

    private final CustomCardConfigRepository customCardConfigRepository;
    private final CardRepository cardRepository;
    private final S3Service s3Service;

    @Autowired
    public CustomCardConfigService(CustomCardConfigRepository customCardConfigRepository,
                                   CardRepository cardRepository,
                                   S3Service s3Service) {
        this.customCardConfigRepository = customCardConfigRepository;
        this.cardRepository = cardRepository;
        this.s3Service = s3Service;
    }

    public CustomCardConfig createOrUpdateCustomCardConfig(CustomCardConfigRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (!StringUtils.hasText(name)) {
            throw new BadRequestError("Name is required");
        }

        CustomCardConfig config = StringUtils.hasText(request.getUuid())
                ? customCardConfigRepository.findByUuid(request.getUuid())
                : null;
        if (config == null) {
            config = new CustomCardConfig();
            if (StringUtils.hasText(request.getUuid())) {
                config.setUuid(request.getUuid());
            } else {
                config.assignUUID();
            }
        }
        assertNameIsUnique(name, config);

        config.setName(name);
        config.setDataRule(request.getDataRule());
        if (StringUtils.hasText(request.getHtmlFileS3Key())) {
            config.setHtmlFileS3Key(request.getHtmlFileS3Key());
        }
        config.setVoided(request.isVoided());
        applyTranslations(config, request.getTranslations());
        return customCardConfigRepository.save(config);
    }

    private void applyTranslations(CustomCardConfig config, Map<String, String> requested) {
        if (requested == null) {
            return;
        }
        JsonObject normalized = new JsonObject();
        for (Map.Entry<String, String> entry : requested.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (key.isEmpty() || normalized.containsKey(key)) {
                continue;
            }
            normalized.put(key, entry.getValue() == null ? "" : entry.getValue());
        }
        config.setTranslations(normalized.isEmpty() ? null : normalized);
    }

    public void deleteConfig(String uuid) {
        CustomCardConfig config = loadByUuid(uuid);
        if (cardRepository.existsByCustomCardConfigIdAndIsVoidedFalse(config.getId())) {
            throw new BadRequestError("Custom card config '%s' is in use by one or more report cards and cannot be deleted", config.getName());
        }
        config.setVoided(true);
        config.setName(EntityUtil.getVoidedName(config.getName(), config.getId()));
        customCardConfigRepository.save(config);
    }

    public String uploadHtmlFile(String uuid, MultipartFile file) throws IOException {
        CustomCardConfig config = loadByUuid(uuid);
        if (file == null || file.isEmpty()) {
            throw new BadRequestError("HTML file is required");
        }
        String fileName = format("%s.html", config.getUuid());
        String targetPath = format("%s/%s", CUSTOM_CARD_CONFIGS_SUBDIR, fileName);
        logger.info(format("CustomCardConfig uploadHtmlFile: uuid=%s targetPath='%s' originalFileName='%s' size=%d",
                uuid, targetPath, file.getOriginalFilename(), file.getSize()));
        File tempFile = AvniFiles.convertMultiPartToFile(file, ".html");
        s3Service.uploadImageFile(tempFile, targetPath);
        config.setHtmlFileS3Key(fileName);
        customCardConfigRepository.save(config);
        logger.info(format("CustomCardConfig uploadHtmlFile completed: uuid=%s htmlFileS3Key='%s'", uuid, fileName));
        return fileName;
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return customCardConfigRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    private CustomCardConfig loadByUuid(String uuid) {
        CustomCardConfig config = customCardConfigRepository.findByUuid(uuid);
        if (config == null) {
            throw new BadRequestError("CustomCardConfig with uuid '%s' not found", uuid);
        }
        return config;
    }

    private void assertNameIsUnique(String name, CustomCardConfig currentConfig) {
        CustomCardConfig existing = customCardConfigRepository.findByNameIgnoreCaseAndIsVoidedFalse(name);
        if (existing != null && !existing.getUuid().equals(currentConfig.getUuid())) {
            throw new BadRequestError("Custom card config with name '%s' already exists", name);
        }
    }
}
