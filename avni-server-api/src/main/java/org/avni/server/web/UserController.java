package org.avni.server.web;

import com.amazonaws.services.cognitoidp.model.AWSCognitoIdentityProviderException;
import com.amazonaws.services.cognitoidp.model.UsernameExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.apache.commons.validator.routines.EmailValidator;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.projection.UserWebProjection;
import org.avni.server.service.*;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.metabase.MetabaseService;
import org.avni.server.util.PhoneNumberUtil;
import org.avni.server.util.RegionUtil;
import org.avni.server.util.ValidationUtil;
import org.avni.server.util.WebResponseUtil;
import org.avni.server.web.request.ChangePasswordRequest;
import org.avni.server.web.request.ResetPasswordRequest;
import org.avni.server.web.request.UserContract;
import org.avni.server.web.request.syncAttribute.UserSyncSettings;
import org.avni.server.web.validation.ValidationException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RepositoryRestController
//@RestController
public class UserController {
    private final CatchmentRepository catchmentRepository;
    private final Logger logger;
    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final UserService userService;
    private final IdpServiceFactory idpServiceFactory;
    private final AccountAdminService accountAdminService;
    private final AccountRepository accountRepository;
    private final AccountAdminRepository accountAdminRepository;
    private final ResetSyncService resetSyncService;
    private final SubjectTypeRepository subjectTypeRepository;
    private final AccessControlService accessControlService;
    private final OrganisationConfigService organisationConfigService;
    private final MetabaseService metabaseService;

    private final Pattern NAME_INVALID_CHARS_PATTERN = Pattern.compile("^.*[<>=\"].*$");

    @Autowired
    public UserController(CatchmentRepository catchmentRepository,
                          UserRepository userRepository,
                          OrganisationRepository organisationRepository,
                          UserService userService,
                          IdpServiceFactory idpServiceFactory,
                          AccountAdminService accountAdminService, AccountRepository accountRepository,
                          AccountAdminRepository accountAdminRepository, ResetSyncService resetSyncService,
                          SubjectTypeRepository subjectTypeRepository,
                          AccessControlService accessControlService, OrganisationConfigService organisationConfigService,
                          MetabaseService metabaseService) {
        this.catchmentRepository = catchmentRepository;
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
        this.userService = userService;
        this.idpServiceFactory = idpServiceFactory;
        this.accountAdminService = accountAdminService;
        this.accountRepository = accountRepository;
        this.accountAdminRepository = accountAdminRepository;
        this.resetSyncService = resetSyncService;
        this.subjectTypeRepository = subjectTypeRepository;
        this.accessControlService = accessControlService;
        this.organisationConfigService = organisationConfigService;
        this.metabaseService = metabaseService;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    private Boolean usernameExists(String name) {
        return (userRepository.findByUsername(name) != null);
    }

    @RequestMapping(value = {"/user", "/user/accountOrgAdmin"}, method = RequestMethod.POST)
    @Transactional
    public ResponseEntity createUser(@RequestBody UserContract userContract) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);
        try {
            if (isUserNameInvalid(userContract.getUsername())) {
                throw new ValidationException(String.format("Invalid username %s", userContract.getUsername()));
            }

            if (usernameExists(userContract.getUsername().trim())) {
                throw new ValidationException(String.format("Username %s already exists", userContract.getUsername()));
            }

            User user = new User();
            user.setUuid(UUID.randomUUID().toString());
            logger.info(String.format("Creating user with username '%s' and UUID '%s'", userContract.getUsername(), user.getUuid()));

            user.setUsername(userContract.getUsername().trim());
            user = setUserAttributes(user, userContract, getRegionForUser(userContract));

            User savedUser = userService.save(user);

            if (savedUser.getOrganisationId() != null) {
                idpServiceFactory.getIdpService(organisationRepository.findOne(savedUser.getOrganisationId())).createUserWithPassword(savedUser, userContract.getPassword(), organisationConfigService.getOrganisationConfigByOrgId(savedUser.getOrganisationId()));
            } else {
                idpServiceFactory.getIdpService().createSuperAdmin(savedUser, userContract.getPassword());
            }
            accountAdminService.createAccountAdmins(savedUser, userContract.getAccountIds());
            userService.addToDefaultUserGroup(savedUser);
            userService.associateUserToGroups(savedUser, userContract.getGroupIds());
            logger.info(String.format("Saved new user '%s', UUID '%s'", userContract.getUsername(), savedUser.getUuid()));
            return new ResponseEntity<>(savedUser, HttpStatus.CREATED);
        } catch (ValidationException | UsernameExistsException ex) {
            return WebResponseUtil.createBadRequestResponse(ex, logger);
        } catch (AWSCognitoIdentityProviderException | IDPException ex) {
            return WebResponseUtil.createInternalServerErrorResponse(ex, logger);
        }
    }

    @GetMapping(value = "/user/{id}")
    @ResponseBody
    public UserContract getUser(@PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);
        User user = userRepository.findOne(id);
        if (user == null) {
            throw new EntityNotFoundException(String.format("User not found with id %d", id));
        }
        UserContract userContract = UserContract.fromEntity(user);
        userContract.setSyncSettings(UserSyncSettings.toWebResponse(user.getSyncSettings(), subjectTypeRepository));
        return userContract;
    }

    @PutMapping(value = {"/user/{id}", "/user/accountOrgAdmin/{id}"})
    @Transactional
    public ResponseEntity updateUser(@RequestBody UserContract userContract, @PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);
        try {
            User user = userRepository.findByUsername(userContract.getUsername());
            if (user == null)
                return ResponseEntity.badRequest()
                        .body(String.format("User with username '%s' not found", userContract.getUsername()));
            User currentUser = userService.getCurrentUser();
            user.setAuditInfo(currentUser);
            resetSyncService.recordSyncAttributeValueChangeForUser(user, userContract, UserSyncSettings.fromUserSyncWebJSON(userContract.getSyncSettings(), subjectTypeRepository));

            String region = getRegionForUser(userContract);
            user = setUserAttributes(user, userContract, region);

            idpServiceFactory.getIdpService(user, userService.isAdmin(user)).updateUser(user);
            userService.save(user);
            accountAdminService.createAccountAdmins(user, userContract.getAccountIds());
            List<UserGroup> associatedUserGroups = userService.associateUserToGroups(user, userContract.getGroupIds());
            if (organisationConfigService.isMetabaseSetupEnabled(UserContextHolder.getOrganisation())) {
                metabaseService.upsertUsersOnMetabase(associatedUserGroups);
            }
            logger.info(String.format("Saved user '%s', UUID '%s'", userContract.getUsername(), user.getUuid()));
            return new ResponseEntity<>(user, HttpStatus.CREATED);
        } catch (ValidationException ex) {
            return WebResponseUtil.createBadRequestResponse(ex, logger);
        } catch (AWSCognitoIdentityProviderException ex) {
            return WebResponseUtil.createInternalServerErrorResponse(ex, logger);
        }
    }

    private String getRegionForUser(UserContract userContract) {
        String region;
        if (userContract.getAccountIds().isEmpty()) {
            region = RegionUtil.getCurrentUserRegion();
        } else {
            region = accountRepository.findOne(userContract.getAccountIds().get(0)).getRegion();
        }
        return region;
    }

    private Boolean emailIsValid(String email) {
        return EmailValidator.getInstance().isValid(email);
    }

    private Boolean isUserNameInvalid(String userName) {
        return ValidationUtil.checkNullOrEmptyOrContainsDisallowedCharacters(userName, ValidationUtil.COMMON_INVALID_CHARS_PATTERN);
    }

    private Boolean isNameInvalid(String name) {
        return ValidationUtil.checkNullOrEmptyOrContainsDisallowedCharacters(name, NAME_INVALID_CHARS_PATTERN);
    }

    private Boolean phoneNumberIsValid(String phoneNumber, String region) {
        return PhoneNumberUtil.isValidPhoneNumber(phoneNumber, region);
    }

    private User setUserAttributes(User user, UserContract userContract, String userRegion) {
        if (!emailIsValid(userContract.getEmail()))
            throw new ValidationException(String.format("Invalid email address %s", userContract.getEmail()));
        user.setEmail(userContract.getEmail());
        if(user.isDisabledInCognito() && !userContract.isDisabledInCognito()){
            user.setLastActivatedDateTime(new DateTime());
        }
        user.setDisabledInCognito(userContract.isDisabledInCognito());
        userService.setPhoneNumber(userContract.getPhoneNumber(), user, userRegion);

        if (isUserNameInvalid(userContract.getUsername())) {
            throw new ValidationException(String.format("Invalid username %s", userContract.getUsername()));
        }

        if (isNameInvalid(userContract.getName())) {
            throw new ValidationException(String.format("Invalid name %s", userContract.getName()));
        }

        user.setName(userContract.getName().trim());
        if (userContract.getCatchmentId() != null) {
            user.setCatchment(catchmentRepository.findOne(userContract.getCatchmentId()));
        }

        user.setOperatingIndividualScope(OperatingIndividualScope.valueOf(userContract.getOperatingIndividualScope()));
        user.setSettings(userContract.getSettings());
        user.setIgnoreSyncSettingsInDEA(userContract.isIgnoreSyncSettingsInDEA());
        user.setSyncSettings(UserSyncSettings.fromUserSyncWebJSON(userContract.getSyncSettings(), subjectTypeRepository));
        User currentUser = userService.getCurrentUser();
        Long organisationId = null;
        if (!userContract.isAdmin()) {
            organisationId = userContract.getOrganisationId() == null ? UserContextHolder.getUserContext().getOrganisationId() : userContract.getOrganisationId();
        }
        user.setOrganisationId(organisationId);
        user.setAuditInfo(currentUser);
        return user;
    }

    @RequestMapping(value = "/user/{id}", method = RequestMethod.DELETE)
    @Transactional
    public ResponseEntity deleteUser(@PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);
        return userService.deleteUser(id);
    }

    @RequestMapping(value = {"/user/{id}/disable", "/user/accountOrgAdmin/{id}/disable"}, method = RequestMethod.PUT)
    @Transactional
    public ResponseEntity disableUser(@PathVariable("id") Long id,
                                      @RequestParam(value = "disable", required = false, defaultValue = "false") boolean disable) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);
        try {
            User user = userRepository.findOne(id);
            User currentUser = userService.getCurrentUser();
            user.setAuditInfo(currentUser);
            boolean isAdmin = userService.isAdmin(user);
            if (disable) {
                idpServiceFactory.getIdpService(user, isAdmin).disableUser(user);
                user.setDisabledInCognito(true);
                userRepository.save(user);
                logger.info(String.format("Disabled user '%s', UUID '%s'", user.getUsername(), user.getUuid()));
            } else {
                if (user.isDisabledInCognito()) {
                    idpServiceFactory.getIdpService(user, isAdmin).enableUser(user);
                    user.setLastActivatedDateTime(new DateTime());
                    user.setDisabledInCognito(false);
                    userRepository.save(user);
                    logger.info(String.format("Enabled previously disabled user '%s', UUID '%s'", user.getUsername(), user.getUuid()));
                } else {
                    logger.info(String.format("User '%s', UUID '%s' already enabled", user.getUsername(), user.getUuid()));
                }
            }
            return new ResponseEntity<>(user, HttpStatus.CREATED);
        } catch (AWSCognitoIdentityProviderException ex) {
            return WebResponseUtil.createInternalServerErrorResponse(ex, logger);
        }
    }

    @RequestMapping(value = {"/user/resetPassword"}, method = RequestMethod.PUT)
    @Transactional
    public ResponseEntity resetPassword(@RequestBody ResetPasswordRequest resetPasswordRequest) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);
        try {
            User user = userRepository.findOne(resetPasswordRequest.getUserId());
            idpServiceFactory.getIdpService(user, userService.isAdmin(user)).resetPassword(user, resetPasswordRequest.getPassword());
            return new ResponseEntity<>(user, HttpStatus.CREATED);
        } catch (AWSCognitoIdentityProviderException ex) {
            return WebResponseUtil.createInternalServerErrorResponse(ex, logger);
        } catch (IDPException ex) {
            return WebResponseUtil.createBadRequestResponse(ex, logger);
        }
    }

    @RequestMapping(value = {"/user/changePassword"}, method = RequestMethod.PUT)
    @Transactional
    public ResponseEntity changePassword(@RequestBody ChangePasswordRequest changePasswordRequest) {
        try {
            User user = userRepository.findOne(UserContextHolder.getUser().getId());
            idpServiceFactory.getIdpService(user, userService.isAdmin(user)).resetPassword(user, changePasswordRequest.getNewPassword());
            return new ResponseEntity<>(user, HttpStatus.CREATED);
        } catch (AWSCognitoIdentityProviderException ex) {
            return WebResponseUtil.createInternalServerErrorResponse(ex, logger);
        } catch (IDPException ex) {
            return WebResponseUtil.createBadRequestResponse(ex, logger);
        }
    }

    @GetMapping(value = "/user/search/find")
    @ResponseBody
    public Page<User> find(@RequestParam(value = "username", required = false) String username,
                           @RequestParam(value = "name", required = false) String name,
                           @RequestParam(value = "email", required = false) String email,
                           @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
                           Pageable pageable) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);
        Long organisationId = UserContextHolder.getUserContext().getOrganisation().getId();
        return userRepository.findAll((root, query, builder) -> {
            Predicate predicate = builder.equal(root.get("organisationId"), organisationId);
            return applyUserPredicates(username, name, email, phoneNumber, root, builder, predicate);
        }, pageable);
    }

    private Predicate applyUserPredicates(@RequestParam(value = "username", required = false) String username, @RequestParam(value = "name", required = false) String name, @RequestParam(value = "email", required = false) String email, @RequestParam(value = "phoneNumber", required = false) String phoneNumber, Root<User> root, CriteriaBuilder builder, Predicate predicate) {
        if (username != null) {
            predicate = builder.and(predicate, builder.like(builder.upper(root.get("username")), "%" + username.toUpperCase() + "%"));
        }
        if (name != null) {
            predicate = builder.and(predicate, builder.like(builder.upper(root.get("name")), "%" + name.toUpperCase() + "%"));
        }
        if (email != null) {
            predicate = builder.and(predicate, builder.like(builder.upper(root.get("email")), "%" + email.toUpperCase() + "%"));
        }
        if (phoneNumber != null) {
            predicate = builder.and(predicate, builder.like(root.get("phoneNumber"), "%" + phoneNumber + "%"));
        }
        return predicate;
    }

    @GetMapping(value = "/user/accountOrgAdmin/search/find")
    @ResponseBody
    public Page<UserContract> findOrgAdmin(@RequestParam(value = "username", required = false) String username,
                                           @RequestParam(value = "name", required = false) String name,
                                           @RequestParam(value = "email", required = false) String email,
                                           @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
                                           Pageable pageable) {
        accessControlService.assertIsSuperAdmin();
        User user = UserContextHolder.getUserContext().getUser();
        List<Long> userAccountIds = getOwnedAccountIds(user);
        Page<UserContract> userContracts = userRepository.findAccountAndOrgAdmins(username, name, email, phoneNumber, userAccountIds, pageable)
                .map(UserContract::fromEntity);
        userContracts.forEach(this::setAccountIds);
        return userContracts;
    }

    @GetMapping(value = "/user/accountOrgAdmin/{id}")
    @ResponseBody
    public UserContract getOne(@PathVariable("id") Long id) {
        accessControlService.assertIsSuperAdmin();
        User user = UserContextHolder.getUserContext().getUser();
        List<Long> userAccountIds = getOwnedAccountIds(user);
        List<Long> organisationIds = getOwnedOrganisationIds(user);
        List<Long> queryParam = organisationIds.isEmpty() ? null : organisationIds;
        UserContract userContract = UserContract.fromEntity(userRepository.getOne(id, userAccountIds, queryParam));
        setAccountIds(userContract);
        return userContract;
    }

    @GetMapping(value = "/user/search/findAll")
    @ResponseBody
    public List<UserWebProjection> getAll() {
        accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        return userRepository.findAllByOrganisationIdAndIsVoidedFalse(organisation.getId());
    }

    private List<Long> getOwnedAccountIds(User user) {
        return accountRepository.findAllByAccountAdmin_User_Id(user.getId()).stream().map(Account::getId).collect(Collectors.toList());
    }

    private void setAccountIds(UserContract uc) {
        List<Long> accountIds = accountRepository.findAllByAccountAdmin_User_Id(uc.getId()).stream().map(Account::getId).collect(Collectors.toList());
        boolean isAdmin = accountAdminRepository.findByUser_Id(uc.getId()).size() > 0;
        uc.setAccountIds(accountIds);
        uc.setAdmin(isAdmin);
    }

    private List<Long> getOwnedOrganisationIds(User user) {
        return organisationRepository.findByAccount_AccountAdmin_User_Id(user.getId()).stream()
                .map(Organisation::getId).collect(Collectors.toList());
    }

    @GetMapping("/user/search/findByOrganisation")
    @ResponseBody
    public Page<User> getUsersByOrganisation(@RequestParam("organisationId") Long organisationId, Pageable pageable) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);
        return userRepository.findByOrganisationIdAndIsVoidedFalse(organisationId, pageable);
    }

    @GetMapping(path = "/user/search/findAllById")
    @ResponseBody
    public List<User> findByIdIn(@RequestParam Long[] ids) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);
        return userRepository.findByIdIn(ids);
    }
}
