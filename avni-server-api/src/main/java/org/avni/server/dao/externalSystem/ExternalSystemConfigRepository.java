package org.avni.server.dao.externalSystem;

import org.avni.messaging.domain.GlificSystemConfig;
import org.avni.messaging.domain.exception.GlificNotConfiguredException;
import org.avni.server.dao.AvniCrudRepository;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.domain.extenalSystem.SystemName;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Not exported over Spring Data REST: rows hold Eka's encrypted secrets and Glific's password, and any user of the
// organisation could read them there.
@Repository
@RepositoryRestResource(exported = false)
public interface ExternalSystemConfigRepository extends AvniCrudRepository<ExternalSystemConfig, Long> {
    ExternalSystemConfig findBySystemName(SystemName systemName);

    ExternalSystemConfig findBySystemNameAndOrganisationId(SystemName systemName, Long organisationId);

    List<ExternalSystemConfig> findAllBySystemNameAndIsVoidedFalse(SystemName systemName);

    @Query(value = "select * from external_system_config where system_name = 'Eka' and not is_voided and config ->> 'webhookId' = :webhookId",
            nativeQuery = true)
    ExternalSystemConfig findEkaByWebhookId(@Param("webhookId") String webhookId);

    // The Eka settings whose webhook ID this is: an Eka webhook's address names its organisation this way.
    default Optional<EkaSystemConfig> findEkaSystemConfigByWebhookId(String webhookId) {
        if (webhookId == null || webhookId.isBlank()) return Optional.empty();
        return Optional.ofNullable(findEkaByWebhookId(webhookId)).map(EkaSystemConfig::new);
    }

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
