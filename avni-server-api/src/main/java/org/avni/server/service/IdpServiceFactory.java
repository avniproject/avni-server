package org.avni.server.service;

import com.auth0.jwk.SigningKeyNotFoundException;
import org.avni.server.config.IdpType;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IdpServiceFactory {
    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired(required = false)
    private CognitoIdpService cognitoIdpService;

    @Autowired(required = false)
    private KeycloakIdpService keycloakIdpService;

    @Autowired(required = false)
    private NoopIdpService noopIdpService;

    @Autowired(required = false)
    private CognitoAuthServiceImpl cognitoAuthService;

    @Autowired(required = false)
    private KeycloakAuthService keycloakAuthService;

    @Value("${avni.idp.type}")
    private IdpType idpType;

    @Autowired
    private OrganisationConfigService organisationConfigService;
    @Autowired
    private UserService userService;

    public IdpServiceFactory() {
    }

    public IdpServiceFactory(OrganisationRepository organisationRepository, CognitoIdpService cognitoIdpService, KeycloakIdpService keycloakIdpService, CognitoAuthServiceImpl cognitoAuthService, KeycloakAuthService keycloakAuthService, IdpType idpType, OrganisationConfigService organisationConfigService, UserService userService) {
        this.organisationRepository = organisationRepository;
        this.cognitoIdpService = cognitoIdpService;
        this.keycloakIdpService = keycloakIdpService;
        this.cognitoAuthService = cognitoAuthService;
        this.keycloakAuthService = keycloakAuthService;
        this.idpType = idpType;
        this.organisationConfigService = organisationConfigService;
        this.userService = userService;
    }

    public IdpService getIdpService() {
        if (idpType.equals(IdpType.none))
            return noopIdpService;

        if (cognitoIdpService != null)
            return cognitoIdpService;

        return keycloakIdpService;
    }

    public IdpService getIdpService(Organisation organisation) {
        OrganisationConfig.Settings settings = getSettings(organisation);

        if (settings.useKeycloakAsIdp())
            return keycloakIdpService;

        if (idpType.equals(IdpType.none))
            return noopIdpService;

        return cognitoIdpService;
    }

    public IdpService getIdpService(User user) {
        if (userService.isAdmin(user)) {
            return getIdpService();
        }
        Organisation organisation = organisationRepository.findOne(user.getOrganisationId());
        return getIdpService(organisation);
    }

    public IAMAuthService getAuthService() {
        switch (idpType) {
            case cognito:
                return cognitoAuthService;
            case keycloak:
                return keycloakAuthService;
            case both:
                return new CompositeIAMAuthService(cognitoAuthService, keycloakAuthService);
            case none:
                return new NoIAMAuthService();
            default:
                throw new RuntimeException(String.format("IdpType: %s is not supported", idpType));
        }
    }

    private OrganisationConfig.Settings getSettings(Organisation organisation) {
        OrganisationConfig organisationConfig = organisationConfigService.getOrganisationConfig(organisation);
        return organisationConfig.getSettingsObject();
    }

    public static class CompositeIAMAuthService implements IAMAuthService {
        private final CognitoAuthServiceImpl cognitoAuthService;
        private final KeycloakAuthService keycloakAuthService;

        public CompositeIAMAuthService(CognitoAuthServiceImpl cognitoAuthService, KeycloakAuthService keycloakAuthService) {
            this.cognitoAuthService = cognitoAuthService;
            this.keycloakAuthService = keycloakAuthService;
        }

        @Override
        public User getUserFromToken(String token) throws SigningKeyNotFoundException {
            try {
                return cognitoAuthService.getUserFromToken(token);
            } catch (SigningKeyNotFoundException e) {
                return keycloakAuthService.getUserFromToken(token);
            }
        }
    }

    public static class NoIAMAuthService implements IAMAuthService {
        @Override
        public User getUserFromToken(String token) throws SigningKeyNotFoundException {
            return null;
        }
    }
}
