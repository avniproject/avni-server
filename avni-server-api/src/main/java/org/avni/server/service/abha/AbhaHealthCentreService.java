package org.avni.server.service.abha;

import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.UserSettings;
import org.avni.server.web.validation.ValidationException;
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

    // For an admin's save of a user's whole settings: the admin app and POST /users. Only a changed ID is checked: the
    // admin app sends the stored one back with every save, and a health centre since dropped from the Eka list must not
    // stop the rest of the user's record from saving.
    public void assertHealthCentreIsOneOfTheOrganisations(JsonObject settings, JsonObject storedSettings, Long organisationId) {
        if (settings == null) return;
        Object hipId = settings.get(UserSettings.ABHA_HIP_ID);
        Object storedHipId = storedSettings == null ? null : storedSettings.get(UserSettings.ABHA_HIP_ID);
        if (hipId != null && hipId.equals(storedHipId)) return;
        // An empty string clears the setting. Anything else is stored as sent, so it must match a health centre exactly.
        if (hipId instanceof String && !((String) hipId).isEmpty() && !isHealthCentreOf(organisationId, (String) hipId)) {
            throw new ValidationException(notAHealthCentreMessage((String) hipId));
        }
    }

    public static String notAHealthCentreMessage(String hipId) {
        return String.format("ABHA Health Facility ID '%s' is not one of this organisation's health centres", hipId);
    }
}
