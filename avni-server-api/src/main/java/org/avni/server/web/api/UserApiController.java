package org.avni.server.web.api;

import com.amazonaws.services.cognitoidp.model.NotAuthorizedException;
import jakarta.persistence.EntityNotFoundException;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.AuthService;
import org.avni.server.service.IdpService;
import org.avni.server.service.IdpServiceFactory;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.auth.ActivateUserRequest;
import org.avni.server.web.request.auth.GenerateTokenRequest;
import org.avni.server.web.request.auth.GenerateTokenResult;
import org.avni.server.web.response.auth.ActivateUserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class UserApiController {
    private final AuthService authService;
    private final UserRepository userRepository;
    private final IdpServiceFactory idpServiceFactory;
    private final AccessControlService accessControlService;
    private static final Logger logger = LoggerFactory.getLogger(UserApiController.class);

    public UserApiController(AuthService authService, UserRepository userRepository, IdpServiceFactory idpServiceFactory, AccessControlService accessControlService) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.idpServiceFactory = idpServiceFactory;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/api/user/generateToken", method = RequestMethod.POST)
    public ResponseEntity<GenerateTokenResult> generateTokenForUser(@RequestBody GenerateTokenRequest request)
            throws BadRequestError, EntityNotFoundException, AvniAccessException, NotAuthorizedException {
        if (request == null || !StringUtils.hasText(request.getUsername())) {
            throw new BadRequestError("User has invalid/empty username");
        }
        if (request == null || !StringUtils.hasText(request.getPassword())) {
            throw new BadRequestError("User has invalid/empty password");
        }
        User user = userRepository.findByUsernameIgnoreCaseAndIsVoidedFalse(request.getUsername());
        if (user == null) {
            throw new EntityNotFoundException("User not-found / is-voided with username: " + request.getUsername());
        }
        if (!user.getUserSettings().isAllowedToInvokeTokenGenerationAPI()) {
            throw AvniAccessException.createForUserNotAllowedTokenGeneration(user);
        }
        return ResponseEntity.ok(new GenerateTokenResult(authService.generateTokenForUser(user.getUsername(), request.getPassword())));
    }

    @RequestMapping(value = "/api/user/activate", method = RequestMethod.POST)
    public ResponseEntity<ActivateUserResponse> activateUser(@RequestBody ActivateUserRequest request) throws EntityNotFoundException {
        this.accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);

        ActivateUserResponse activateUserResponse = new ActivateUserResponse();
        activateUserResponse.setUserName(request.getUsername());
        try {
            User user = userRepository.findByUsername(request.getUsername());
            if (user == null) {
                activateUserResponse.setSuccess(false);
                activateUserResponse.setErrorMessage("User not found");
                return new ResponseEntity<>(activateUserResponse, HttpStatus.BAD_REQUEST);
            }
            IdpService idpService = idpServiceFactory.getIdpService();
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
