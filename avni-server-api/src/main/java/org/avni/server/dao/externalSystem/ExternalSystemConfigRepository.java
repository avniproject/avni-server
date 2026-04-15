package org.avni.server.dao.externalSystem;

import org.avni.messaging.domain.GlificSystemConfig;
import org.avni.messaging.domain.WatiSystemConfig;
import org.avni.messaging.domain.exception.GlificNotConfiguredException;
import org.avni.messaging.domain.exception.WatiNotConfiguredException;
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

    /**
     * Fetches the shared Wati config stored under the platform org.
     * Unlike Glific (per-org config), Wati uses one account across all orgs,
     * so organisationId here is the platform org ID (from avni.wati.platformOrgId),
     * not the calling org's ID.
     */
    default WatiSystemConfig getWatiSystemConfig(Long platformOrgId) throws WatiNotConfiguredException {
        ExternalSystemConfig watiConfig = findBySystemNameAndOrganisationId(SystemName.Wati, platformOrgId);
        if (watiConfig == null) {
            throw new WatiNotConfiguredException("Wati system config not found under platform org with id: " + platformOrgId);
        }
        return new WatiSystemConfig(watiConfig);
    }
}
