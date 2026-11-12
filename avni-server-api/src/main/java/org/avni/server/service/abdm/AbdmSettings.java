package org.avni.server.service.abdm;

import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

// The ABDM settings of the organisation the thread acts for: its Eka settings (HIP IDs and names, HIU ID, clinic and
// doctor, record-sharing client), saved by a super administrator (PUT /abha/admin/ekaConfig).
@Component
public class AbdmSettings {
    private final AbdmOrgContext orgContext;
    private final ExternalSystemConfigRepository externalSystemConfigRepository;

    @Autowired
    public AbdmSettings(AbdmOrgContext orgContext, ExternalSystemConfigRepository externalSystemConfigRepository) {
        this.orgContext = orgContext;
        this.externalSystemConfigRepository = externalSystemConfigRepository;
    }

    // No ABDM work can be done for an organisation without them.
    public EkaSystemConfig current() {
        return find().orElseThrow(() -> AbdmRequestException.conflict("Eka is not set up for this organisation"));
    }

    public Optional<EkaSystemConfig> find() {
        Long organisationId = orgContext.organisationId();
        if (organisationId == null) return Optional.empty();
        try {
            return Optional.of(externalSystemConfigRepository.getEkaSystemConfig(organisationId));
        } catch (EkaNotConfiguredException e) {
            return Optional.empty();
        }
    }
}
