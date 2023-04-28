package org.avni.server.framework.security;

import com.auth0.jwk.SigningKeyNotFoundException;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.service.GroupPrivilegeService;
import org.avni.server.service.IAMAuthService;
import org.avni.server.service.IdpServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {
    public final static SimpleGrantedAuthority USER_AUTHORITY = new SimpleGrantedAuthority(User.USER);
    public final static SimpleGrantedAuthority ADMIN_AUTHORITY = new SimpleGrantedAuthority(User.ADMIN);
    public final static SimpleGrantedAuthority ORGANISATION_ADMIN_AUTHORITY = new SimpleGrantedAuthority(User.ORGANISATION_ADMIN);
    public final static List<SimpleGrantedAuthority> ALL_AUTHORITIES = Arrays.asList(USER_AUTHORITY, ADMIN_AUTHORITY, ORGANISATION_ADMIN_AUTHORITY);
    public static final String AUTH_SEPARATOR = "<#@#>";
    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final AccountAdminRepository accountAdminRepository;
    private final IdpServiceFactory idpServiceFactory;
    private final GroupPrivilegeService groupPrivilegeService;
    private final UserGroupRepository userGroupRepository;
    private final GroupPrivilegeRepository groupPrivilegeRepository;

    @Autowired
    public AuthService(UserRepository userRepository, OrganisationRepository organisationRepository,
                       AccountAdminRepository accountAdminRepository, IdpServiceFactory idpServiceFactory,
                       GroupPrivilegeService groupPrivilegeService, UserGroupRepository userGroupRepository, GroupPrivilegeRepository groupPrivilegeRepository) {
        this.idpServiceFactory = idpServiceFactory;
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
        this.accountAdminRepository = accountAdminRepository;
        this.groupPrivilegeService = groupPrivilegeService;
        this.userGroupRepository = userGroupRepository;
        this.groupPrivilegeRepository = groupPrivilegeRepository;
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
            userContext = changeUser(iamAuthService.getUserFromToken(authToken), organisationUUID);
        } catch (SigningKeyNotFoundException signingKeyNotFoundException) {
            throw new RuntimeException(signingKeyNotFoundException);
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
        if (user.isAdmin() && organisationUUID != null) {
            user.setOrgAdmin(true);
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

        authorities = fetchConsolidatedAuthorities(userContext, authorities);

        if (authorities.isEmpty()) return null;
        return createTempAuth(authorities);
    }

    private List<SimpleGrantedAuthority> fetchConsolidatedAuthorities(UserContext userContext, List<SimpleGrantedAuthority> authorities) {
        boolean shouldSetAllowToTrue = (userContext.getUser().isOrgAdmin()
                || !CollectionUtils.isEmpty(
                        userGroupRepository.findByUserAndGroupHasAllPrivilegesTrueAndIsVoidedFalse(userContext.getUser())));
        Group group = new Group();
        group.setOrganisationId(userContext.getOrganisationId());
        List<GroupPrivilege> allGeneratedPrivileges = groupPrivilegeService.generateAllPrivileges(group, shouldSetAllowToTrue);
        List<GroupPrivilege> configuredGroupPrivileges = groupPrivilegeRepository.getAllViewAndActionAllowedPrivilegesForUser(userContext.getUser().getId());
        configuredGroupPrivileges.addAll(allGeneratedPrivileges);
        List<GroupPrivilege> consolidatedGroupPrivileges = configuredGroupPrivileges.stream().distinct().collect(Collectors.toList());
        List<SimpleGrantedAuthority> groupPrivilegeContractList = consolidatedGroupPrivileges.stream()
                .map(gp -> {
                    String typeUUID = gp.getTypeUUID();
                    StringBuilder role = new StringBuilder(gp.getOrganisationId() + AUTH_SEPARATOR);
                    role.append(gp.getPrivilege().getEntityType().toString() + AUTH_SEPARATOR);
                    if (typeUUID != null) role.append(gp.getTypeUUID() + AUTH_SEPARATOR);
                    role.append(gp.getPrivilege().getName());
                    return new SimpleGrantedAuthority(role.toString());
                })
                .distinct()
                .collect(Collectors.toList());
        groupPrivilegeContractList.addAll(authorities);
        return groupPrivilegeContractList;
    }

    private UserContext changeUser(User user, String organisationUUID) {
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
