package org.avni.server.web;

import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.OrganisationContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class OrganisationController implements RestControllerResourceProcessor<Organisation> {
    private final OrganisationRepository organisationRepository;
    private final AccountRepository accountRepository;
    private final ImplementationRepository implementationRepository;
    private final AccessControlService accessControlService;
    private final OrganisationService organisationService;
    private final OrganisationCategoryRepository organisationCategoryRepository;
    private final OrganisationStatusRepository organisationStatusRepository;

    @Value("${avni.default.org.user.db.password}")
    private String AVNI_DEFAULT_ORG_USER_DB_PASSWORD;

    @Autowired
    public OrganisationController(OrganisationRepository organisationRepository, AccountRepository accountRepository, ImplementationRepository implementationRepository, AccessControlService accessControlService, OrganisationService organisationService, OrganisationCategoryRepository organisationCategoryRepository, OrganisationStatusRepository organisationStatusRepository) {
        this.organisationRepository = organisationRepository;
        this.accountRepository = accountRepository;
        this.implementationRepository = implementationRepository;
        this.accessControlService = accessControlService;
        this.organisationService = organisationService;
        this.organisationCategoryRepository = organisationCategoryRepository;
        this.organisationStatusRepository = organisationStatusRepository;
    }

    @RequestMapping(value = "/organisation", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity save(@RequestBody OrganisationContract request, @RequestParam(value = "sample", defaultValue = "false") boolean sample) {
        accessControlService.assertIsSuperAdmin();
        Organisation org = organisationRepository.findByUuid(request.getUuid());
        implementationRepository.createDBUser(request.getDbUser(), AVNI_DEFAULT_ORG_USER_DB_PASSWORD);
        implementationRepository.createImplementationSchema(request.getSchemaName(), request.getDbUser());
        if (org == null) {
            org = new Organisation();
        }
        org.setUuid(request.getUuid() == null ? UUID.randomUUID().toString() : request.getUuid());
        org.setDbUser(request.getDbUser());
        org.setSchemaName(request.getSchemaName());
        org.setCategory(organisationCategoryRepository.findEntity(request.getCategoryId()));
        org.setStatus(organisationStatusRepository.findEntity(request.getStatusId()));
        setAttributesOnOrganisation(request, org);
        setOrgAccountByIdOrDefault(org, request.getAccountId());

        organisationRepository.save(org);
        organisationService.setupBaseOrganisationData(org);
        if (sample) {
            organisationService.setupSampleOrganisationData(org);
        }
        return new ResponseEntity<>(org, HttpStatus.CREATED);
    }

    @RequestMapping(value = "/organisation", method = RequestMethod.GET)
    public List<OrganisationContract> findAll() {
        accessControlService.assertIsSuperAdmin();
        User user = UserContextHolder.getUserContext().getUser();
        List<Organisation> organisations = organisationRepository.findByAccount_AccountAdmin_User_Id(user.getId());
        return organisations.stream().map(OrganisationContract::fromEntity).collect(Collectors.toList());
    }

    @RequestMapping(value = "/organisation/{id}", method = RequestMethod.GET)
    public OrganisationContract findById(@PathVariable Long id) {
        accessControlService.assertIsSuperAdmin();
        User user = UserContextHolder.getUserContext().getUser();
        Organisation organisation = organisationRepository.findByIdAndAccount_AccountAdmin_User_Id(id, user.getId());
        return organisation != null ? OrganisationContract.fromEntity(organisation) : null;
    }

    @RequestMapping(value = "/organisation/current", method = RequestMethod.GET)
    public OrganisationContract findCurrent() {
        long organisationId = UserContextHolder.getUserContext().getOrganisationId();
        return OrganisationContract.fromEntity(organisationRepository.findOne(organisationId));
    }

    @RequestMapping(value = "/organisation/{id}", method = RequestMethod.PUT)
    @Transactional
    public Organisation updateOrganisation(@PathVariable Long id, @RequestBody OrganisationContract request) throws Exception {
        accessControlService.assertIsSuperAdmin();
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
        accessControlService.assertIsSuperAdmin();
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
            Predicate accountPredicate = builder.or(predicates.toArray(new Predicate[0]));
            return builder.and(accountPredicate, predicate);
        }, pageable);
        return organisations.map(OrganisationContract::fromEntity);
    }

    @RequestMapping(value = "organisation/search/findAllById", method = RequestMethod.GET)
    public Page<Organisation> findAllById(@Param("ids") Long[] ids, Pageable pageable) {
        accessControlService.assertIsSuperAdmin();
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
        organisation.setCategory(organisationCategoryRepository.findEntity(request.getCategoryId()));
        organisation.setStatus(organisationStatusRepository.findEntity(request.getStatusId()));
    }

    private void setOrgAccountByIdOrDefault(Organisation organisation, Long accountId) {
        User user = UserContextHolder.getUserContext().getUser();
        Account account = accountId == null ? accountRepository.findAllByAccountAdmin_User_Id(user.getId()).stream().findFirst().orElse(null)
                : accountRepository.findOne(accountId);
        organisation.setAccount(account);
    }
}
