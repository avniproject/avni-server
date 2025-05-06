package org.avni.server.service;

import org.avni.server.dao.ArchivalConfigRepository;
import org.avni.server.domain.ArchivalConfig;
import org.avni.server.web.contract.ArchivalConfigContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ArchivalConfigService {

    private final ArchivalConfigRepository archivalConfigRepository;

    @Autowired
    public ArchivalConfigService(ArchivalConfigRepository archivalConfigRepository) {
        this.archivalConfigRepository = archivalConfigRepository;
    }

    public ArchivalConfig saveOrUpdate(ArchivalConfigContract archivalConfig) {
        ArchivalConfig existingArchivalConfig = archivalConfigRepository.findByUuid(archivalConfig.getUuid());
        ArchivalConfig archivalConfigToSaveOrUpdate = existingArchivalConfig != null ? existingArchivalConfig : new ArchivalConfig();
        archivalConfigToSaveOrUpdate.setSqlQuery(archivalConfig.getSqlQuery());
        archivalConfigToSaveOrUpdate.setRealmQuery(archivalConfig.getRealmQuery());
        archivalConfigToSaveOrUpdate.setBatchSize(archivalConfig.getBatchSize());
        archivalConfigToSaveOrUpdate.setVoided(archivalConfig.isVoided());
        archivalConfigToSaveOrUpdate.assignUUIDIfRequired();
        archivalConfigToSaveOrUpdate.updateAudit();
        return archivalConfigRepository.save(archivalConfigToSaveOrUpdate);
    }

}
