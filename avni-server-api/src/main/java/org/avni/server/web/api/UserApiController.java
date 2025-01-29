package org.avni.server.web.api;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.OperatingIndividualScope;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.AuthService;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.IDPException;
import org.avni.server.service.IdpService;
import org.avni.server.service.IdpServiceFactory;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.PhoneNumberUtil;
import org.avni.server.util.RegionUtil;
import org.avni.server.web.request.auth.ActivateUserRequest;
import org.avni.server.web.request.auth.CreateUserRequest;
import org.avni.server.web.request.auth.GenerateTokenRequest;
import org.avni.server.web.request.auth.GenerateTokenResult;
import org.avni.server.web.response.auth.ActivateUserResponse;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class UserApiController {
    private final AuthService authService;
    private final UserRepository userRepository;
    private final IdpServiceFactory idpServiceFactory;
    private final AccessControlService accessControlService;
    private final OrganisationConfigRepository organisationConfigRepository;
    private final OrganisationRepository organisationRepository;
    private static final Logger logger = LoggerFactory.getLogger(UserApiController.class);

    public UserApiController(AuthService authService, UserRepository userRepository, IdpServiceFactory idpServiceFactory, AccessControlService accessControlService, OrganisationConfigRepository organisationConfigRepository, OrganisationRepository organisationRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.idpServiceFactory = idpServiceFactory;
        this.accessControlService = accessControlService;
        this.organisationConfigRepository = organisationConfigRepository;
        this.organisationRepository = organisationRepository;
    }

    @RequestMapping(value = "/api/user/generateToken", method = RequestMethod.POST)
    public ResponseEntity<GenerateTokenResult> generateTokenForUser(@RequestBody GenerateTokenRequest request) throws EntityNotFoundException {
        User user = userRepository.findByUsername(request.getUsername());
        if (user == null) {
            throw new EntityNotFoundException("User not found with username: " + request.getUsername());
        }
        if (!user.getUserSettings().isAllowedToInvokeTokenGenerationAPI()) {
            throw AvniAccessException.createForUserNotAllowedTokenGeneration(user);
        }
        return ResponseEntity.ok(new GenerateTokenResult(authService.generateTokenForUser(request.getUsername(), request.getPassword())));
    }

    @RequestMapping(value = "/api/user", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity<String> createInactiveUser(@RequestBody CreateUserRequest createUserRequest) throws IDPException {
        this.accessControlService.assertIsSuperAdmin();

        User user = new User();
        user.assignUUID();
        user.setUsername(createUserRequest.getUsername());
        user.setEmail(createUserRequest.getEmail());
        user.setName(createUserRequest.getName());
        user.setPhoneNumber(PhoneNumberUtil.getStandardFormatPhoneNumber(createUserRequest.getPhoneNumber(), RegionUtil.getCurrentUserRegion()));
        user.setOrganisationId(createUserRequest.getOrganisationId());
        user.setCreatedBy(UserContextHolder.getUser());
        user.setLastModifiedBy(UserContextHolder.getUser());
        user.setCreatedDateTime(new DateTime());
        user.setLastModifiedDateTime(new DateTime());
        user.setOperatingIndividualScope(OperatingIndividualScope.None);
        User savedUser = userRepository.save(user);
        OrganisationConfig organisationConfig = organisationConfigRepository.findByOrganisationId(createUserRequest.getOrganisationId());
        Organisation organisation = organisationRepository.findOne(createUserRequest.getOrganisationId());
        IdpService idpService = idpServiceFactory.getIdpService(organisation);
        idpService.createInActiveUser(user, organisationConfig);
        return ResponseEntity.ok(savedUser.getUuid());
    }

    @RequestMapping(value = "/api/user/activate", method = RequestMethod.POST)
    public ResponseEntity<ActivateUserResponse> activateUser(@RequestBody ActivateUserRequest request) throws EntityNotFoundException {
        this.accessControlService.checkOrgPrivilege(PrivilegeType.EditUserConfiguration);

        ActivateUserResponse activateUserResponse = new ActivateUserResponse();
        activateUserResponse.setUserName(request.getUsername());
        try {
            User user = userRepository.findByUsername(request.getUsername());
            if (user == null) {
                activateUserResponse.setSuccess(false);
                activateUserResponse.setErrorMessage("User not found");
                return new ResponseEntity<>(activateUserResponse, HttpStatus.BAD_REQUEST);
            }
            IdpService idpService = idpServiceFactory.getIdpService(UserContextHolder.getOrganisation());
            idpService.activateUser(user);
            activateUserResponse.setSuccess(true);
            return new ResponseEntity<>(activateUserResponse, HttpStatus.OK);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            activateUserResponse.setSuccess(false);
            activateUserResponse.setErrorMessage(e.getMessage());
            return new ResponseEntity<>(activateUserResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
