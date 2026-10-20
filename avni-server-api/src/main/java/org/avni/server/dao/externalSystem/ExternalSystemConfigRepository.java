package org.avni.server.dao.externalSystem;

import org.avni.messaging.domain.GlificSystemConfig;
import org.avni.messaging.domain.exception.GlificNotConfiguredException;
import org.avni.server.dao.AvniCrudRepository;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.domain.extenalSystem.SystemName;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExternalSystemConfigRepository extends AvniCrudRepository<ExternalSystemConfig, Long> {
    ExternalSystemConfig findBySystemName(SystemName systemName);

    ExternalSystemConfig findBySystemNameAndOrganisationId(SystemName systemName, Long organisationId);

    @RestResource(exported = false)
    List<ExternalSystemConfig> findAllBySystemNameAndIsVoidedFalse(SystemName systemName);

    default GlificSystemConfig getGlificSystemConfig(Long organisationId) throws GlificNotConfiguredException {
        ExternalSystemConfig glificConfig = findBySystemNameAndOrganisationId(SystemName.Glific, organisationId);
        if (glificConfig == null) {
            throw new GlificNotConfiguredException("Glific system config not found for organisation with id: " + organisationId);
        }
        return new GlificSystemConfig(glificConfig);
    }

    default EkaSystemConfig getEkaSystemConfig(Long organisationId) throws EkaNotConfiguredException {
        ExternalSystemConfig ekaConfig = findBySystemNameAndOrganisationId(SystemName.Eka, organisationId);
        if (ekaConfig == null || ekaConfig.isVoided()) {
            throw new EkaNotConfiguredException("Eka system config not found for organisation with id: " + organisationId);
        }
        return new EkaSystemConfig(ekaConfig);
    }
}
