package org.avni.server.web.api;

import com.amazonaws.services.cognitoidp.model.NotAuthorizedException;
import jakarta.persistence.EntityNotFoundException;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.framework.security.AuthService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.auth.GenerateTokenRequest;
import org.avni.server.web.request.auth.GenerateTokenResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class UserLoginApiController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public UserLoginApiController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
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
}
