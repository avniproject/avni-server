package org.avni.server.service.abha;

import jakarta.persistence.EntityManager;
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
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.avni.server.domain.extenalSystem.EkaSystemConfig.*;

// A super administrator stores an organisation's Eka settings. The caller sends no ORGANISATION-UUID header, so the
// database's per-organisation row filter is off: the HIP ID check sees every organisation, and the row is written with
// the target organisation's id set here.
@Service
public class AbhaAdminService {
    private static final Set<String> ENVIRONMENTS = Set.of("sandbox", "production");
    private static final long EKA_SETTINGS_SAVE_LOCK = 1087_000_001L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EntityManager entityManager;
    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;
    private final ExternalSystemConfigRepository externalSystemConfigRepository;
    private final EkaCredentialCipher ekaCredentialCipher;

    @Autowired
    public AbhaAdminService(EntityManager entityManager,
                            OrganisationRepository organisationRepository,
                            UserRepository userRepository,
                            ExternalSystemConfigRepository externalSystemConfigRepository,
                            EkaCredentialCipher ekaCredentialCipher) {
        this.entityManager = entityManager;
        this.organisationRepository = organisationRepository;
        this.userRepository = userRepository;
        this.externalSystemConfigRepository = externalSystemConfigRepository;
        this.ekaCredentialCipher = ekaCredentialCipher;
    }

    @Transactional
    public EkaConfigResponse saveEkaConfig(String organisationUUID, EkaConfigRequest request) {
        Organisation organisation = findOrganisation(organisationUUID);
        Long organisationId = organisation.getId();
        if (request.getEnvironment() == null || !ENVIRONMENTS.contains(request.getEnvironment())) {
            throw new BadRequestError("environment must be sandbox or production, not %s", request.getEnvironment());
        }
        if (!StringUtils.hasText(request.getClientId())) {
            throw new BadRequestError("clientId is required");
        }
        List<String> hipIds = validHipIds(request.getHipIds());
        lockEkaSettingsSaves();
        assertNoHipIdListedElsewhere(organisationId, hipIds);

        ExternalSystemConfig row = externalSystemConfigRepository.findBySystemNameAndOrganisationId(SystemName.Eka, organisationId);
        if (row == null) {
            row = new ExternalSystemConfig();
            row.setSystemName(SystemName.Eka);
            row.assignUUIDIfRequired();
            row.setOrganisationId(organisationId);
        }
        JsonObject stored = row.getConfig() == null ? new JsonObject() : row.getConfig();
        String clientId = request.getClientId().trim();
        if (!StringUtils.hasText(request.getClientSecret())) {
            if (stored.getString(ENCRYPTED_CLIENT_SECRET) == null) {
                throw new BadRequestError("clientSecret is required when Eka is first set up for an organisation");
            }
            // The stored secret belongs to the stored client and environment; it would fail every login for another.
            if (!clientId.equals(stored.getString(CLIENT_ID)) || !request.getEnvironment().equals(stored.getString(ENVIRONMENT))) {
                throw new BadRequestError("clientSecret is required when clientId or environment changes");
            }
        }
        String encryptedClientSecret = encryptOrKeep(request.getClientSecret(), stored.getString(ENCRYPTED_CLIENT_SECRET));
        String avniSystemUser = StringUtils.hasText(request.getAvniSystemUser())
                ? userOfOrganisation(request.getAvniSystemUser(), organisationId)
                : stored.getString(AVNI_SYSTEM_USER);
        String sharingClientId = setClearOrKeep(request.getSharingClientId(), stored.getString(SHARING_CLIENT_ID));
        // The phone is handed the M1 client's token, so record sharing needs a client of its own.
        if (sharingClientId != null && sharingClientId.trim().equals(clientId)) {
            throw new BadRequestError("sharingClientId must be a different Eka client from clientId");
        }
        String encryptedSharingClientSecret = sharingClientSecret(request, sharingClientId, stored);
        row.setConfig(new JsonObject()
                .with(ENVIRONMENT, request.getEnvironment())
                .with(CLIENT_ID, clientId)
                .with(ENCRYPTED_CLIENT_SECRET, encryptedClientSecret)
                .with(HIP_IDS, hipIds)
                .with(AVNI_SYSTEM_USER, avniSystemUser)
                .with(ENCRYPTED_WEBHOOK_SIGNING_KEY, encryptOrKeep(request.getWebhookSigningKey(), stored.getString(ENCRYPTED_WEBHOOK_SIGNING_KEY)))
                .with(HIP_NAMES, hipNames(request.getHipNames(), stored, hipIds))
                .with(HIU_ID, setClearOrKeep(request.getHiuId(), stored.getString(HIU_ID)))
                .with(CLINIC_ID, setClearOrKeep(request.getClinicId(), stored.getString(CLINIC_ID)))
                .with(DOCTOR_OID, setClearOrKeep(request.getDoctorOid(), stored.getString(DOCTOR_OID)))
                .with(WEBHOOK_ID, StringUtils.hasText(stored.getString(WEBHOOK_ID)) ? stored.getString(WEBHOOK_ID) : newWebhookId())
                .with(SHARING_CLIENT_ID, sharingClientId)
                .with(ENCRYPTED_SHARING_CLIENT_SECRET, encryptedSharingClientSecret));
        row.setVoided(false);
        ExternalSystemConfig saved = externalSystemConfigRepository.save(row);
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

    // Saves of Eka settings run one at a time, across organisations, until commit: two first saves cannot make two rows,
    // and two organisations cannot claim one HIP ID at once.
    private void lockEkaSettingsSaves() {
        entityManager.createNativeQuery("select count(*) from pg_advisory_xact_lock(:key)")
                .setParameter("key", EKA_SETTINGS_SAVE_LOCK)
                .getSingleResult();
    }

    // Optional until the check-in story needs it, and kept when a later save leaves it out; when given, it must be a
    // user of this organisation.
    private String userOfOrganisation(String username, Long organisationId) {
        User user = userRepository.findByUsernameIgnoreCaseAndIsVoidedFalse(username.trim());
        if (user == null || !organisationId.equals(user.getOrganisationId())) {
            throw new BadRequestError("avniSystemUser %s is not a user of this organisation", username);
        }
        return user.getUsername();
    }

    private String encryptOrKeep(String plaintext, String storedEncrypted) {
        return StringUtils.hasText(plaintext) ? ekaCredentialCipher.encrypt(plaintext) : storedEncrypted;
    }

    // Left out keeps the stored value, blank clears it, anything else replaces it.
    private static String setClearOrKeep(String given, String stored) {
        if (given == null) return stored;
        return StringUtils.hasText(given) ? given.trim() : null;
    }

    // Checked as the M1 secret is: the stored one belongs to the stored sharing client and environment.
    private String sharingClientSecret(EkaConfigRequest request, String sharingClientId, JsonObject stored) {
        boolean secretGiven = StringUtils.hasText(request.getSharingClientSecret());
        if (sharingClientId == null) {
            if (secretGiven) throw new BadRequestError("sharingClientSecret is given without a sharingClientId");
            return null;
        }
        if (secretGiven) return ekaCredentialCipher.encrypt(request.getSharingClientSecret());
        String storedSecret = stored.getString(ENCRYPTED_SHARING_CLIENT_SECRET);
        if (storedSecret == null) {
            throw new BadRequestError("sharingClientSecret is required when a sharing client is first set up");
        }
        if (!sharingClientId.equals(stored.getString(SHARING_CLIENT_ID)) || !request.getEnvironment().equals(stored.getString(ENVIRONMENT))) {
            throw new BadRequestError("sharingClientSecret is required when sharingClientId or environment changes");
        }
        return storedSecret;
    }

    // Names only for the centres listed; when left out, the stored names of centres still listed are kept.
    @SuppressWarnings("unchecked")
    private static Map<String, String> hipNames(Map<String, String> given, JsonObject stored, List<String> hipIds) {
        Map<String, String> names = new LinkedHashMap<>();
        if (given == null) {
            Object storedNames = stored.get(HIP_NAMES);
            if (storedNames instanceof Map) {
                ((Map<String, String>) storedNames).forEach((hipId, name) -> {
                    if (hipIds.contains(hipId)) names.put(hipId, name);
                });
            }
            return names;
        }
        for (Map.Entry<String, String> entry : given.entrySet()) {
            String hipId = entry.getKey() == null ? null : entry.getKey().trim();
            if (!hipIds.contains(hipId)) {
                throw new BadRequestError("hipNames names %s, which is not in hipIds", entry.getKey());
            }
            if (!StringUtils.hasText(entry.getValue())) {
                throw new BadRequestError("hipNames must not give %s a blank name", hipId);
            }
            names.put(hipId, entry.getValue().trim());
        }
        return names;
    }

    // Part of the webhook address Eka is given: random, opaque, and never changed once made.
    private static String newWebhookId() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
