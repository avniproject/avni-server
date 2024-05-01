package org.avni.server.dao.externalSystem;

import org.avni.messaging.domain.GlificSystemConfig;
import org.avni.messaging.domain.exception.GlificNotConfiguredException;
import org.avni.server.dao.AvniCrudRepository;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.domain.extenalSystem.SystemName;
import org.springframework.stereotype.Repository;

@Repository
public interface ExternalSystemConfigRepository extends AvniCrudRepository<ExternalSystemConfig, Long> {
    ExternalSystemConfig findBySystemName(SystemName systemName);

    ExternalSystemConfig findBySystemNameAndOrganisationId(SystemName systemName, Long organisationId);

    default GlificSystemConfig getGlificSystemConfig(Long organisationId) throws GlificNotConfiguredException {
        ExternalSystemConfig glificConfig = findBySystemNameAndOrganisationId(SystemName.Glific, organisationId);
        if (glificConfig == null) {
            throw new GlificNotConfiguredException("Glific system config not found for organisation with id: " + organisationId);
        }
        return new GlificSystemConfig(glificConfig);
    }
}
