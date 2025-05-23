package org.avni.server.service;

import org.avni.server.dao.ArchivalConfigRepository;
import org.avni.server.domain.ArchivalConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Component
public class ArchivalTasklet implements Tasklet {
    private static final Logger logger = LoggerFactory.getLogger(ArchivalTasklet.class);

    private final JdbcTemplate jdbcTemplate;
    private final ArchivalConfigRepository archivalConfigRepository;

    public ArchivalTasklet(JdbcTemplate jdbcTemplate, ArchivalConfigRepository archivalConfigRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.archivalConfigRepository = archivalConfigRepository;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<ArchivalConfig> activeConfigs = archivalConfigRepository.findAll();

        for (ArchivalConfig config : activeConfigs) {
            try {
                int updated = jdbcTemplate.update(config.getSqlQuery());
                logger.info("Updated {} records using archival config: {}", updated, config.getUuid());
            } catch (Exception e) {
                logger.error("Error executing archival query for config: " + config.getUuid(), e);
                throw e;
            }
        }

        return RepeatStatus.FINISHED;
    }
}