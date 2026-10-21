package org.avni.server.service.abha;

import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.UserSettings;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.accessControl.SubjectPartitionData;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.accessControl.AccessControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Service
public class AbhaService {
    private static final Logger logger = LoggerFactory.getLogger(AbhaService.class);
    private final OrganisationConfigService organisationConfigService;
    private final SubjectTypeRepository subjectTypeRepository;
    private final AccessControlService accessControlService;
    private final ExternalSystemConfigRepository externalSystemConfigRepository;
    private final AbhaHealthCentreService abhaHealthCentreService;
    private final EkaTokenService ekaTokenService;
    private final AbhaLinkService abhaLinkService;

    @Autowired
    public AbhaService(OrganisationConfigService organisationConfigService,
                       SubjectTypeRepository subjectTypeRepository,
                       AccessControlService accessControlService,
                       ExternalSystemConfigRepository externalSystemConfigRepository,
                       AbhaHealthCentreService abhaHealthCentreService,
                       EkaTokenService ekaTokenService,
                       AbhaLinkService abhaLinkService) {
        this.organisationConfigService = organisationConfigService;
        this.subjectTypeRepository = subjectTypeRepository;
        this.accessControlService = accessControlService;
        this.externalSystemConfigRepository = externalSystemConfigRepository;
        this.abhaHealthCentreService = abhaHealthCentreService;
        this.ekaTokenService = ekaTokenService;
        this.abhaLinkService = abhaLinkService;
    }

    // The checks behind every /abha endpoint, in order: ABHA on, a known subject type, the right to register or edit
    // it, Eka settings stored, and the user's health centre among the organisation's.
    public AbhaAccess checkAccess(String subjectTypeUUID) {
        requireAbhaEnabled();
        SubjectType subjectType = StringUtils.hasText(subjectTypeUUID) ? subjectTypeRepository.findByUuid(subjectTypeUUID) : null;
        if (subjectType == null) {
            throw new AbhaRefusedException(AbhaRefusal.ABHA_UNKNOWN_SUBJECT_TYPE);
        }
        // After the null check: the privilege check passes silently for a subject type it cannot find.
        try {
            accessControlService.checkHasAnyOfSpecificSubjectPrivileges(Arrays.asList(PrivilegeType.RegisterSubject, PrivilegeType.EditSubject), subjectType.getUuid());
        } catch (AvniAccessException e) {
            throw new AbhaRefusedException(AbhaRefusal.ABHA_NO_PRIVILEGE);
        }
        return checkEkaSettingsAndHealthCentre();
    }

    // The checks of checkAccess that name no subject type, for an endpoint that checks the patient itself: ABHA on, Eka
    // settings stored, and the user's health centre among the organisation's.
    public AbhaAccess checkOrganisationAccess() {
        requireAbhaEnabled();
        return checkEkaSettingsAndHealthCentre();
    }

    // A patient on the server within what the user's phone may hold: catchment, direct assignment, sync attributes.
    public void checkSubjectWithinReach(Individual individual) {
        if (!accessControlService.checkSubjectAccess(individual, SubjectPartitionData.create(individual)).isPassed()) {
            throw new AbhaRefusedException(AbhaRefusal.ABHA_NO_PRIVILEGE);
        }
    }

    private void requireAbhaEnabled() {
        if (!organisationConfigService.isAbhaEnabled()) {
            throw new AbhaRefusedException(AbhaRefusal.ABHA_DISABLED);
        }
    }

    private AbhaAccess checkEkaSettingsAndHealthCentre() {
        UserContext userContext = UserContextHolder.getUserContext();
        User user = userContext.getUser();
        Long organisationId = userContext.getOrganisationId();
        EkaSystemConfig ekaSystemConfig;
        try {
            ekaSystemConfig = externalSystemConfigRepository.getEkaSystemConfig(organisationId);
        } catch (EkaNotConfiguredException e) {
            throw new AbhaRefusedException(AbhaRefusal.ABHA_NOT_CONFIGURED);
        }
        String hipId = user.getSettings() == null ? null : user.getSettings().getString(UserSettings.ABHA_HIP_ID);
        if (!abhaHealthCentreService.isHealthCentreOf(organisationId, hipId)) {
            throw new AbhaRefusedException(AbhaRefusal.ABHA_NO_HEALTH_CENTRE);
        }
        return new AbhaAccess(user, organisationId, ekaSystemConfig, hipId);
    }

    // The organisation's Eka access token, or the refusal the app reads. The token is a bearer credential for the
    // organisation at Eka; never log it.
    public EkaToken ekaAccessToken(Long organisationId) {
        try {
            return ekaTokenService.getAccessToken(organisationId);
        } catch (EkaNotConfiguredException e) {
            throw new AbhaRefusedException(AbhaRefusal.ABHA_NOT_CONFIGURED);
        } catch (EkaAuthException e) {
            // EkaTokenService has logged it, without the key.
            throw new AbhaRefusedException(AbhaRefusal.ABHA_EKA_UNAVAILABLE);
        }
    }

    public AbhaSession createSession(String subjectTypeUUID) {
        AbhaAccess access = checkAccess(subjectTypeUUID);
        EkaToken token = ekaAccessToken(access.getOrganisationId());
        // The key is a bearer credential for the organisation at Eka; never log it.
        logger.info("ABHA session key issued to user {} of organisation {}", access.getUser().getId(), access.getOrganisationId());
        return new AbhaSession(token.getAccessToken(), token.getExpiresAt(), access.getEkaSystemConfig().getEnvironment(), access.getHipId());
    }

    // The patients of the subject type in the organisation holding an ABHA number, after the checks of /abha/session,
    // whose privilege is on that subject type only. Never logs the number.
    public List<String> findSubjectUUIDs(String subjectTypeUUID, String abhaNumber) {
        checkAccess(subjectTypeUUID);
        if (AbhaLinkService.digitsOnly(abhaNumber).length() != AbhaLinkService.ABHA_NUMBER_DIGITS) {
            throw new AbhaRefusedException(AbhaRefusal.ABHA_NUMBER_INVALID);
        }
        // Found: checkAccess refuses a subject type it cannot find.
        return abhaLinkService.findSubjectUUIDs(subjectTypeRepository.findByUuid(subjectTypeUUID), abhaNumber);
    }
}
