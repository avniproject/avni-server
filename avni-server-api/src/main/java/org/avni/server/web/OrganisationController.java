package org.avni.server.web;

import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.OrganisationContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.persistence.criteria.Predicate;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class OrganisationController implements RestControllerResourceProcessor<Organisation> {
    private final OrganisationRepository organisationRepository;
    private final AccountRepository accountRepository;
    private final GenderRepository genderRepository;
    private final OrganisationConfigRepository organisationConfigRepository;
    private final GroupRepository groupRepository;
    private final ImplementationRepository implementationRepository;
    private final AccessControlService accessControlService;

    @Autowired
    public OrganisationController(OrganisationRepository organisationRepository, AccountRepository accountRepository, GenderRepository genderRepository, OrganisationConfigRepository organisationConfigRepository, GroupRepository groupRepository, ImplementationRepository implementationRepository, AccessControlService accessControlService) {
        this.organisationRepository = organisationRepository;
        this.accountRepository = accountRepository;
        this.genderRepository = genderRepository;
        this.organisationConfigRepository = organisationConfigRepository;
        this.groupRepository = groupRepository;
        this.implementationRepository = implementationRepository;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/organisation", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity save(@RequestBody OrganisationContract request) {
        accessControlService.checkIsAdmin();
        String tempPassword = "password";
        Organisation org = organisationRepository.findByUuid(request.getUuid());
        implementationRepository.createDBUser(request.getDbUser(), tempPassword);
        implementationRepository.createImplementationSchema(request.getSchemaName(), request.getDbUser());
        if (org == null) {
            org = new Organisation();
        }
        org.setUuid(request.getUuid() == null ? UUID.randomUUID().toString() : request.getUuid());
        org.setDbUser(request.getDbUser());
        org.setSchemaName(request.getSchemaName());
        setAttributesOnOrganisation(request, org);
        setOrgAccountByIdOrDefault(org, request.getAccountId());

        organisationRepository.save(org);
        createDefaultGenders(org);
        addDefaultGroup(org.getId());
        createDefaultOrgConfig(org);

        return new ResponseEntity<>(org, HttpStatus.CREATED);
    }

    private void createDefaultOrgConfig(Organisation org) {
        OrganisationConfig organisationConfig = new OrganisationConfig();
        organisationConfig.assignUUID();
        Map<String, Object> settings = new HashMap<>();
        settings.put("languages", new String[]{"en"});
        JsonObject jsonObject = new JsonObject(settings);
        organisationConfig.setSettings(jsonObject);
        organisationConfig.setOrganisationId(org.getId());
        organisationConfigRepository.save(organisationConfig);
    }

    private void createDefaultGenders(Organisation org) {
        createGender("Male", org);
        createGender("Female", org);
        createGender("Other", org);
    }

    private void createGender(String genderName, Organisation org) {
        Gender gender = new Gender();
        gender.setName(genderName);
        gender.assignUUID();
        gender.setOrganisationId(org.getId());
        genderRepository.save(gender);
    }

    private void addDefaultGroup(Long organisationId){
        Group group = new Group();
        group.setName(Group.Everyone);
        group.setOrganisationId(organisationId);
        group.setUuid(UUID.randomUUID().toString());
        group.setHasAllPrivileges(true);
        group.setVersion(0);
        groupRepository.save(group);
    }

    @RequestMapping(value = "/organisation", method = RequestMethod.GET)
    public List<OrganisationContract> findAll() {
        accessControlService.checkIsAdmin();
        User user = UserContextHolder.getUserContext().getUser();
        List<Organisation> organisations = organisationRepository.findByAccount_AccountAdmin_User_Id(user.getId());
        return organisations.stream().map(OrganisationContract::fromEntity).collect(Collectors.toList());
    }

    @RequestMapping(value = "/organisation/{id}", method = RequestMethod.GET)
    public OrganisationContract findById(@PathVariable Long id) {
        accessControlService.checkIsAdmin();
        User user = UserContextHolder.getUserContext().getUser();
        Organisation organisation = organisationRepository.findByIdAndAccount_AccountAdmin_User_Id(id, user.getId());
        return organisation != null ? OrganisationContract.fromEntity(organisation) : null;
    }

    @RequestMapping(value = "/organisation/{id}", method = RequestMethod.PUT)
    @Transactional
    public Organisation updateOrganisation(@PathVariable Long id, @RequestBody OrganisationContract request) throws Exception {
        accessControlService.checkIsAdmin();
        User user = UserContextHolder.getUserContext().getUser();
        Organisation organisation = organisationRepository.findByIdAndAccount_AccountAdmin_User_Id(id, user.getId());
        if (organisation == null) {
            throw new Exception(String.format("Organisation %s not found", request.getName()));
        }
        setAttributesOnOrganisation(request, organisation);
        setOrgAccountByIdOrDefault(organisation, request.getAccountId());
        implementationRepository.createImplementationSchema(organisation.getSchemaName(), organisation.getDbUser());
        return organisationRepository.save(organisation);
    }


    @RequestMapping(value = "/organisation/search/find", method = RequestMethod.GET)
    @ResponseBody
    public Page<OrganisationContract> find(@RequestParam(value = "name", required = false) String name,
                                           @RequestParam(value = "dbUser", required = false) String dbUser,
                                           Pageable pageable) {
        accessControlService.checkIsAdmin();
        User user = UserContextHolder.getUserContext().getUser();
        List<Account> ownedAccounts = accountRepository.findAllByAccountAdmin_User_Id(user.getId());
        Page<Organisation> organisations = organisationRepository.findAll((root, query, builder) -> {
            Predicate predicate = builder.equal(root.get("isVoided"), false);
            if (name != null) {
                predicate = builder.and(predicate, builder.like(builder.upper(root.get("name")), "%" + name.toUpperCase() + "%"));
            }
            if (dbUser != null) {
                predicate = builder.and(predicate, builder.like(builder.upper(root.get("dbUser")), "%" + dbUser.toUpperCase() + "%"));
            }
            List<Predicate> predicates = new ArrayList<>();
            ownedAccounts.forEach(account -> predicates.add(builder.equal(root.get("account"), account)));
            Predicate accountPredicate = builder.or(predicates.toArray(new Predicate[predicates.size()]));
            return builder.and(accountPredicate, predicate);
        }, pageable);
        return organisations.map(OrganisationContract::fromEntity);
    }

    @RequestMapping(value = "organisation/search/findAllById", method = RequestMethod.GET)
    public Page<Organisation> findAllById(@Param("ids") Long[] ids, Pageable pageable) {
        accessControlService.checkIsAdmin();
        return organisationRepository.findAllByIdInAndIsVoidedFalse(ids, pageable);
    }

    private void setAttributesOnOrganisation(@RequestBody OrganisationContract request, Organisation organisation) {
        organisation.setName(request.getName());
        organisation.setUsernameSuffix(request.getUsernameSuffix());
        if (request.getParentOrganisationId() != null) {
            Organisation parentOrg = organisationRepository.findOne(request.getParentOrganisationId());
            organisation.setParentOrganisationId(parentOrg != null ? parentOrg.getId() : null);
        }
        organisation.setMediaDirectory(request.getMediaDirectory());
        organisation.setVoided(request.isVoided());
    }

    private void setOrgAccountByIdOrDefault(Organisation organisation, Long accountId) {
        User user = UserContextHolder.getUserContext().getUser();
        Account account = accountId == null ? accountRepository.findAllByAccountAdmin_User_Id(user.getId()).stream().findFirst().orElse(null)
                : accountRepository.findOne(accountId);
        organisation.setAccount(account);
    }

}
