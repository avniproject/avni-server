package org.avni.server.service.abha;

import jakarta.persistence.EntityNotFoundException;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.domain.extenalSystem.SystemName;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.EkaConfigRequest;
import org.avni.server.web.response.EkaConfigResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.avni.server.domain.extenalSystem.EkaSystemConfig.*;

// A super administrator stores an organisation's Eka settings. The caller sends no ORGANISATION-UUID header, so the
// database's per-organisation row filter is off: the HIP ID check sees every organisation, and the row is written with
// the target organisation's id set here.
@Service
public class AbhaAdminService {
    private static final Set<String> ENVIRONMENTS = Set.of("sandbox", "production");

    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;
    private final ExternalSystemConfigRepository externalSystemConfigRepository;
    private final EkaCredentialCipher ekaCredentialCipher;
    private final EkaTokenService ekaTokenService;

    @Autowired
    public AbhaAdminService(OrganisationRepository organisationRepository,
                            UserRepository userRepository,
                            ExternalSystemConfigRepository externalSystemConfigRepository,
                            EkaCredentialCipher ekaCredentialCipher,
                            EkaTokenService ekaTokenService) {
        this.organisationRepository = organisationRepository;
        this.userRepository = userRepository;
        this.externalSystemConfigRepository = externalSystemConfigRepository;
        this.ekaCredentialCipher = ekaCredentialCipher;
        this.ekaTokenService = ekaTokenService;
    }

    @Transactional
    public EkaConfigResponse saveEkaConfig(String organisationUUID, EkaConfigRequest request) {
        Organisation organisation = findOrganisation(organisationUUID);
        Long organisationId = organisation.getId();
        if (!ENVIRONMENTS.contains(request.getEnvironment())) {
            throw new BadRequestError("environment must be sandbox or production, not %s", request.getEnvironment());
        }
        if (!StringUtils.hasText(request.getClientId())) {
            throw new BadRequestError("clientId is required");
        }
        List<String> hipIds = validHipIds(request.getHipIds());
        assertNoHipIdListedElsewhere(organisationId, hipIds);
        String avniSystemUser = userOfOrganisation(request.getAvniSystemUser(), organisationId);

        ExternalSystemConfig row = externalSystemConfigRepository.findBySystemNameAndOrganisationId(SystemName.Eka, organisationId);
        if (row == null) {
            row = new ExternalSystemConfig();
            row.setSystemName(SystemName.Eka);
            row.assignUUIDIfRequired();
            row.setOrganisationId(organisationId);
        }
        JsonObject stored = row.getConfig() == null ? new JsonObject() : row.getConfig();
        String encryptedClientSecret = encryptOrKeep(request.getClientSecret(), stored.getString(ENCRYPTED_CLIENT_SECRET));
        if (encryptedClientSecret == null) {
            throw new BadRequestError("clientSecret is required when Eka is first set up for an organisation");
        }
        row.setConfig(new JsonObject()
                .with(ENVIRONMENT, request.getEnvironment())
                .with(CLIENT_ID, request.getClientId().trim())
                .with(ENCRYPTED_CLIENT_SECRET, encryptedClientSecret)
                .with(HIP_IDS, hipIds)
                .with(AVNI_SYSTEM_USER, avniSystemUser)
                .with(ENCRYPTED_WEBHOOK_SIGNING_KEY, encryptOrKeep(request.getWebhookSigningKey(), stored.getString(ENCRYPTED_WEBHOOK_SIGNING_KEY))));
        row.setVoided(false);
        ExternalSystemConfig saved = externalSystemConfigRepository.save(row);
        evictTokenAfterCommit(organisationId);
        return new EkaConfigResponse(organisationUUID, new EkaSystemConfig(saved));
    }

    @Transactional(readOnly = true)
    public EkaConfigResponse getEkaConfig(String organisationUUID) {
        Organisation organisation = findOrganisation(organisationUUID);
        try {
            return new EkaConfigResponse(organisationUUID, externalSystemConfigRepository.getEkaSystemConfig(organisation.getId()));
        } catch (EkaNotConfiguredException e) {
            throw new EntityNotFoundException(e.getMessage());
        }
    }

    private Organisation findOrganisation(String organisationUUID) {
        UserContext userContext = UserContextHolder.getUserContext();
        if (!userContext.isActingAsSuperAdmin()) {
            throw new BadRequestError("Send this request without the ORGANISATION-UUID header; organisationUUID names the organisation");
        }
        Organisation organisation = organisationRepository.findByUuidAndAccount_AccountAdmin_User_Id(organisationUUID, userContext.getUser().getId());
        if (organisation == null) {
            throw new EntityNotFoundException(String.format("Organisation %s not found in the accounts you administer", organisationUUID));
        }
        return organisation;
    }

    private static List<String> validHipIds(List<String> hipIds) {
        if (hipIds == null || hipIds.isEmpty()) {
            throw new BadRequestError("hipIds must list at least one health centre ID");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String hipId : hipIds) {
            if (!StringUtils.hasText(hipId)) {
                throw new BadRequestError("hipIds must not contain a blank health centre ID");
            }
            unique.add(hipId.trim());
        }
        return new ArrayList<>(unique);
    }

    private void assertNoHipIdListedElsewhere(Long organisationId, List<String> hipIds) {
        for (ExternalSystemConfig other : externalSystemConfigRepository.findAllBySystemNameAndIsVoidedFalse(SystemName.Eka)) {
            if (organisationId.equals(other.getOrganisationId())) {
                continue;
            }
            for (String hipId : new EkaSystemConfig(other).getHipIds()) {
                if (hipIds.contains(hipId)) {
                    throw new BadRequestError("Health centre ID %s is already listed by another organisation (id %d)", hipId, other.getOrganisationId());
                }
            }
        }
    }

    // Optional until the check-in story needs it; when given, it must be a user of this organisation.
    private String userOfOrganisation(String username, Long organisationId) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        User user = userRepository.findByUsernameIgnoreCaseAndIsVoidedFalse(username.trim());
        if (user == null || !organisationId.equals(user.getOrganisationId())) {
            throw new BadRequestError("avniSystemUser %s is not a user of this organisation", username);
        }
        return user.getUsername();
    }

    private String encryptOrKeep(String plaintext, String storedEncrypted) {
        return StringUtils.hasText(plaintext) ? ekaCredentialCipher.encrypt(plaintext) : storedEncrypted;
    }

    // Evicting before the commit would let a concurrent login read the old settings and cache a token made from them.
    private void evictTokenAfterCommit(Long organisationId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            ekaTokenService.evict(organisationId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ekaTokenService.evict(organisationId);
            }
        });
    }
}
