package org.avni.server.web;

import org.avni.server.dao.CatchmentRepository;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.*;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.accessControl.GroupPrivilegeService;
import org.avni.server.web.request.GroupPrivilegeContract;
import org.avni.server.web.request.UserBulkUploadContract;
import org.avni.server.web.request.UserInfoClientContract;
import org.avni.server.web.request.UserInfoContract;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.SliceImpl;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class UserInfoController implements RestControllerResourceProcessor<UserInfoContract> {
    private final CatchmentRepository catchmentRepository;
    private final Logger logger;
    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final UserService userService;
    private final IdpServiceFactory idpServiceFactory;
    private final OrganisationConfigService organisationConfigService;
    private final GroupPrivilegeService groupPrivilegeService;
    private final AccessControlService accessControlService;

    @Autowired
    public UserInfoController(CatchmentRepository catchmentRepository, UserRepository userRepository, OrganisationRepository organisationRepository, UserService userService,
                              IdpServiceFactory idpServiceFactory, OrganisationConfigService organisationConfigService, GroupPrivilegeService groupPrivilegeService, AccessControlService accessControlService) {
        this.catchmentRepository = catchmentRepository;
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
        this.userService = userService;
        this.idpServiceFactory = idpServiceFactory;
        this.organisationConfigService = organisationConfigService;
        this.groupPrivilegeService = groupPrivilegeService;
        this.accessControlService = accessControlService;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @RequestMapping(value = "/userInfo", method = RequestMethod.GET)
    public ResponseEntity<UserInfoContract> getUserInfo() {
        UserContext userContext = UserContextHolder.getUserContext();
        User user = userContext.getUser();
        Organisation organisation = userContext.getOrganisation();

        if (organisation == null && !user.isAdmin()) {
            logger.info(String.format("Organisation not found for user ID: %s", user.getId()));
            return new ResponseEntity<>(new UserInfoClientContract(), HttpStatus.NOT_FOUND);
        }
        if (user.isAdmin() && organisation == null) {
            organisation = new Organisation();
        }
        return new ResponseEntity<>(getUserInfoObject(organisation, user), HttpStatus.OK);
    }

    /**
     * @return
     * @deprecated as of release 2.9, replaced by {@link #getMyProfile()}
     */
    @Deprecated
    @RequestMapping(value = "/me", method = RequestMethod.GET)
    public ResponseEntity<UserInfoContract> getMyProfileOld() {
        return getUserInfo();
    }

    @RequestMapping(value = "/v2/me", method = RequestMethod.GET)
    public PagedResources<Resource<UserInfoContract>> getMyProfile() {
        UserContext userContext = UserContextHolder.getUserContext();
        User user = userContext.getUser();
        Organisation organisation = userContext.getOrganisation();

        return wrap(new PageImpl<>(Arrays.asList(getUserInfoObject(organisation, user))));
    }

    @RequestMapping(value = "/me/v3", method = RequestMethod.GET)
    public SlicedResources<Resource<UserInfoContract>> getMyProfileAsSlice() {
        UserContext userContext = UserContextHolder.getUserContext();
        User user = userContext.getUser();
        Organisation organisation = userContext.getOrganisation();

        return wrap(new SliceImpl<>(Arrays.asList(getUserInfoObject(organisation, user))));
    }

    public UserInfoContract getUserInfoObject(Organisation organisation, User user) {
        String catchmentName = user.getCatchment() == null ? null : user.getCatchment().getName();
        List<GroupPrivilege> groupPrivileges = groupPrivilegeService.getExplicitGroupPrivileges(user).getPrivileges();
        List<GroupPrivilegeContract> groupPrivilegeContractList = groupPrivileges.stream()
                .map(GroupPrivilegeContract::fromEntity)
                .distinct()
                .collect(Collectors.toList());
        return new UserInfoClientContract(user.getUsername(),
                organisation.getName(),
                organisation.getId(),
                organisation.getEffectiveUsernameSuffix(),
                user.getRoles(),
                user.getSettings(),
                user.getName(),
                catchmentName,
                user.getSyncSettings(),
                groupPrivilegeContractList);
    }

    @RequestMapping(value = "/me", method = RequestMethod.POST)
    @Transactional
    public void saveMyProfile(@RequestBody UserInfoContract userInfo) {
        User user = userService.getCurrentUser();
        user.setSettings(userInfo.getSettings());
        user.setLastModifiedBy(user);
        user.setLastModifiedDateTime(DateTime.now());
        userRepository.save(user);
    }

    @RequestMapping(value = "/users", method = RequestMethod.POST)
    @Transactional
    public void save(@RequestBody UserBulkUploadContract[] userContracts) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);
        Arrays.stream(userContracts).forEach(userContract -> {
            logger.info(String.format("Saving user with UUID/Name %s/%s", userContract.getUuid(), userContract.getName()));
            User user = userContract.getUuid() == null ? userRepository.findByUsername(userContract.getName()) : userRepository.findByUuid(userContract.getUuid());
            boolean newUser = false;
            if (user == null) {
                user = new User();
                user.setUuid(userContract.getUuid() == null ? UUID.randomUUID().toString() : userContract.getUuid());
                user.setUsername(userContract.getName());
                newUser = true;
            }
            Catchment catchment = userContract.getCatchmentUUID() == null ? catchmentRepository.findOne(userContract.getCatchmentId()) : catchmentRepository.findByUuid(userContract.getCatchmentUUID());
            user.setCatchment(catchment);

            Long organisationId = getOrganisationId(userContract);
            user.setOrganisationId(organisationId);
            user.setOperatingIndividualScope(OperatingIndividualScope.valueOf(userContract.getOperatingIndividualScope()));
            user.setSettings(userContract.getSettings());
            user.setPhoneNumber(userContract.getPhoneNumber());
            user.setEmail(userContract.getEmail());
            user.setAuditInfo(userService.getCurrentUser());
            User savedUser = userService.save(user);
            if (newUser) userService.addToDefaultUserGroup(user);
            logger.info(String.format("Saved User with UUID %s", userContract.getUuid()));
            OrganisationConfig organisationConfig = organisationConfigService.getOrganisationConfigByOrgId(organisationId);
            try {
                idpServiceFactory.getIdpService(organisationRepository.findOne(organisationId)).createUserIfNotExists(savedUser, organisationConfig);
            } catch (IDPException e) {
                logger.error(String.format("Error creating user with UUID %s.", userContract.getUuid()), e);
                throw new RuntimeException(e);
            }
        });
    }

    private Long getOrganisationId(UserBulkUploadContract userContract) {
        String uuid = userContract.getOrganisationUUID();
        Long id = userContract.getOrganisationId();
        if (id == null && uuid == null) {
            throw new RuntimeException("Not found: Organisation{uuid=null, id=null}");
        }
        if (id != null) {
            return id;
        }
        Organisation organisation = organisationRepository.findByUuid(uuid);
        if (organisation == null) {
            throw new RuntimeException(String.format("Not found: Organisation{uuid='%s'}", uuid));
        }
        return organisation.getId();
    }

}
