package org.avni.server.framework.security;

import com.auth0.jwk.SigningKeyNotFoundException;
import org.avni.server.dao.AccountAdminRepository;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.AccountAdmin;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.accessControl.AvniNoUserSessionException;
import org.avni.server.service.IAMAuthService;
import org.avni.server.service.IdpServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AuthService {
    public final static SimpleGrantedAuthority USER_AUTHORITY = new SimpleGrantedAuthority(User.USER);
    public final static List<SimpleGrantedAuthority> ALL_AUTHORITIES = Collections.singletonList(USER_AUTHORITY);
    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final AccountAdminRepository accountAdminRepository;
    private final IdpServiceFactory idpServiceFactory;

    @Autowired
    public AuthService(UserRepository userRepository, OrganisationRepository organisationRepository, AccountAdminRepository accountAdminRepository, IdpServiceFactory idpServiceFactory) {
        this.idpServiceFactory = idpServiceFactory;
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
        this.accountAdminRepository = accountAdminRepository;
    }

    public UserContext authenticateByUserName(String username, String organisationUUID) {
        becomeSuperUser();
        return changeUser(userRepository.findByUsername(username), organisationUUID);
    }

    public UserContext authenticateByToken(String authToken, String organisationUUID) {
        becomeSuperUser();
        IAMAuthService iamAuthService = idpServiceFactory.getAuthService();
        UserContext userContext;
        try {
            User userFromToken = iamAuthService.getUserFromToken(authToken);
            userContext = changeUser(userFromToken, organisationUUID);
        } catch (SigningKeyNotFoundException signingKeyNotFoundException) {
            throw new AvniNoUserSessionException(signingKeyNotFoundException);
        }
        userContext.setAuthToken(authToken);
        return userContext;
    }

    public UserContext authenticateByUserId(Long userId, String organisationUUID) {
        becomeSuperUser();
        Optional<User> user = userRepository.findById(userId);
        if (user.isPresent()) {
            return changeUser(user.get(), organisationUUID);
        }
        throw new RuntimeException(String.format("Not found: User{id='%s'}", userId));
    }

    private Authentication attemptAuthentication(User user, String organisationUUID) {
        UserContext userContext = new UserContext();
        UserContextHolder.create(userContext);
        if (user == null) {
            return null;
        }
        List<AccountAdmin> accountAdmins = accountAdminRepository.findByUser_Id(user.getId());
        user.setAdmin(accountAdmins.size() > 0);
        Organisation organisation = null;
        if (organisationUUID != null) {
            organisation = organisationRepository.findByUuid(organisationUUID);
        } else if (user.getOrganisationId() != null) {
            organisation = organisationRepository.findOne(user.getOrganisationId());
        }
        userContext.setUser(user);
        userContext.setOrganisation(organisation);
        userContext.setOrganisationUUID(organisationUUID);

        List<SimpleGrantedAuthority> authorities = ALL_AUTHORITIES.stream()
                .filter(authority -> userContext.getRoles().contains(authority.getAuthority()))
                .collect(Collectors.toList());

        if (authorities.isEmpty()) return null;
        return createTempAuth(authorities);
    }

    private UserContext changeUser(User user, String organisationUUID) {
        if (user == null) {
            throw new AvniNoUserSessionException("No user, or not logged in");
        }
        SecurityContextHolder.getContext().setAuthentication(attemptAuthentication(user, organisationUUID));
        return UserContextHolder.getUserContext();
    }

    private void becomeSuperUser() {
        UserContextHolder.clear();
        SecurityContextHolder.getContext().setAuthentication(createTempAuth(ALL_AUTHORITIES));
    }

    private Authentication createTempAuth(List<SimpleGrantedAuthority> authorities) {
        String token = UUID.randomUUID().toString();
        return new AnonymousAuthenticationToken(token, token, authorities);
    }

}
