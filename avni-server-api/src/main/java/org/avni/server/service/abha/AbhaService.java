package org.avni.server.service.abha;

import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.UserSettings;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
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

@Service
public class AbhaService {
    private static final Logger logger = LoggerFactory.getLogger(AbhaService.class);
    private final OrganisationConfigService organisationConfigService;
    private final SubjectTypeRepository subjectTypeRepository;
    private final AccessControlService accessControlService;
    private final ExternalSystemConfigRepository externalSystemConfigRepository;
    private final AbhaHealthCentreService abhaHealthCentreService;
    private final EkaTokenService ekaTokenService;

    @Autowired
    public AbhaService(OrganisationConfigService organisationConfigService,
                       SubjectTypeRepository subjectTypeRepository,
                       AccessControlService accessControlService,
                       ExternalSystemConfigRepository externalSystemConfigRepository,
                       AbhaHealthCentreService abhaHealthCentreService,
                       EkaTokenService ekaTokenService) {
        this.organisationConfigService = organisationConfigService;
        this.subjectTypeRepository = subjectTypeRepository;
        this.accessControlService = accessControlService;
        this.externalSystemConfigRepository = externalSystemConfigRepository;
        this.abhaHealthCentreService = abhaHealthCentreService;
        this.ekaTokenService = ekaTokenService;
    }

    // The checks behind every /abha endpoint, in order: ABHA on, a known subject type, the right to register or edit
    // it, Eka settings stored, and the user's health centre among the organisation's.
    public AbhaAccess checkAccess(String subjectTypeUUID) {
        UserContext userContext = UserContextHolder.getUserContext();
        User user = userContext.getUser();
        Long organisationId = userContext.getOrganisationId();
        if (!organisationConfigService.isAbhaEnabled()) {
            throw new AbhaRefusedException(AbhaRefusal.ABHA_DISABLED);
        }
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

    public AbhaSession createSession(String subjectTypeUUID) {
        AbhaAccess access = checkAccess(subjectTypeUUID);
        EkaToken token;
        try {
            token = ekaTokenService.getAccessToken(access.getOrganisationId());
        } catch (EkaNotConfiguredException e) {
            throw new AbhaRefusedException(AbhaRefusal.ABHA_NOT_CONFIGURED);
        } catch (EkaAuthException e) {
            // EkaTokenService has logged it, without the key.
            throw new AbhaRefusedException(AbhaRefusal.ABHA_EKA_UNAVAILABLE);
        }
        // The key is a bearer credential for the organisation at Eka; never log it.
        logger.info("ABHA session key issued to user {} of organisation {}", access.getUser().getId(), access.getOrganisationId());
        return new AbhaSession(token.getAccessToken(), token.getExpiresAt(), access.getEkaSystemConfig().getEnvironment(), access.getHipId());
    }
}
