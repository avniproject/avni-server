package org.avni.server.service.abha;

import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

// A user's health centre must be one of the organisation's Eka health facility IDs. An organisation with no Eka
// settings has none, so every ID is refused.
@Service
public class AbhaHealthCentreService {
    private final ExternalSystemConfigRepository externalSystemConfigRepository;

    @Autowired
    public AbhaHealthCentreService(ExternalSystemConfigRepository externalSystemConfigRepository) {
        this.externalSystemConfigRepository = externalSystemConfigRepository;
    }

    public boolean isHealthCentreOf(Long organisationId, String hipId) {
        if (organisationId == null) {
            return false;
        }
        try {
            List<String> hipIds = externalSystemConfigRepository.getEkaSystemConfig(organisationId).getHipIds();
            return hipIds.contains(hipId);
        } catch (EkaNotConfiguredException e) {
            return false;
        }
    }

    public static String notAHealthCentreMessage(String hipId) {
        return String.format("ABHA Health Facility ID '%s' is not one of this organisation's health centres", hipId);
    }
}
