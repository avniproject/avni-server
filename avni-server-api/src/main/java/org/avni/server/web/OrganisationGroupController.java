package org.avni.server.web;

import org.avni.server.dao.AccountRepository;
import org.avni.server.dao.ImplementationRepository;
import org.avni.server.dao.OrganisationGroupRepository;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationGroup;
import org.avni.server.domain.OrganisationGroupOrganisation;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.ReactAdminUtil;
import org.avni.server.web.validation.ValidationException;
import org.avni.server.web.request.OrganisationGroupContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.HashSet;
import java.util.Set;

@RestController
public class OrganisationGroupController implements RestControllerResourceProcessor<OrganisationGroup> {

    @Value("${avni.org.password}")
    private String AVNI_DEFAULT_ORG_USER_DB_PASSWORD;
    private final Logger logger;
    private final OrganisationGroupRepository organisationGroupRepository;
    private final OrganisationRepository organisationRepository;
    private final AccountRepository accountRepository;
    private final ImplementationRepository implementationRepository;
    private final AccessControlService accessControlService;

    public OrganisationGroupController(OrganisationGroupRepository organisationGroupRepository,
                                       OrganisationRepository organisationRepository,
                                       AccountRepository accountRepository, ImplementationRepository implementationRepository, AccessControlService accessControlService) {
        this.organisationGroupRepository = organisationGroupRepository;
        this.organisationRepository = organisationRepository;
        this.accountRepository = accountRepository;
        this.implementationRepository = implementationRepository;
        this.accessControlService = accessControlService;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @RequestMapping(value = "/organisationGroup", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity save(@RequestBody OrganisationGroupContract request) throws Exception {
        accessControlService.assertIsSuperAdmin();
        if (organisationGroupRepository.findByName(request.getName()) != null) {
            throw new ValidationException(String.format("Organisation group %s already exists", request.getName()));
        }
        implementationRepository.createDBUser(request.getDbUser(), AVNI_DEFAULT_ORG_USER_DB_PASSWORD);
        OrganisationGroup organisationGroup = new OrganisationGroup();
        organisationGroup.setDbUser(request.getDbUser());
        organisationGroup.assignUUIDIfRequired();
        saveOrganisationGroup(request, organisationGroup);
        return new ResponseEntity<>(organisationGroup, HttpStatus.CREATED);
    }

    @RequestMapping(value = "/organisationGroup/{id}", method = RequestMethod.PUT)
    @Transactional
    public ResponseEntity<?> updateOrganisationGroup(@PathVariable("id") Long id, @RequestBody OrganisationGroupContract request) throws Exception {
        accessControlService.assertIsSuperAdmin();
        User user = UserContextHolder.getUserContext().getUser();
        OrganisationGroup organisationGroup = organisationGroupRepository.findByIdAndAccount_AccountAdmin_User_Id(id, user.getId());;
        //disable changing dbUser
        saveOrganisationGroup(request, organisationGroup);
        return new ResponseEntity<>(organisationGroup, HttpStatus.OK);
    }

    @RequestMapping(value = "/organisationGroup", method = RequestMethod.GET)
    public Page<OrganisationGroupContract> get(Pageable pageable) {
        accessControlService.assertIsSuperAdmin();
        User user = UserContextHolder.getUserContext().getUser();
        Page<OrganisationGroup> organisationGroups = organisationGroupRepository.findByAccount_AccountAdmin_User_Id(user.getId(), pageable);
        return organisationGroups.map(OrganisationGroupContract::fromEntity);
    }

    @RequestMapping(value = "/organisationGroup/{id}", method = RequestMethod.GET)
    public OrganisationGroupContract getById(@PathVariable Long id) {
        accessControlService.assertIsSuperAdmin();
        User user = UserContextHolder.getUserContext().getUser();
        OrganisationGroup organisationGroup = organisationGroupRepository.findByIdAndAccount_AccountAdmin_User_Id(id, user.getId());
        return OrganisationGroupContract.fromEntity(organisationGroup);
    }

    @RequestMapping(value = "/organisationGroup/{id}", method = RequestMethod.DELETE)
    @Transactional
    public ResponseEntity<?> deleteById(@PathVariable Long id) {
        accessControlService.assertIsSuperAdmin();
        User user = UserContextHolder.getUserContext().getUser();
        OrganisationGroup organisationGroup = organisationGroupRepository.findByIdAndAccount_AccountAdmin_User_Id(id, user.getId());
        if (organisationGroup == null) {
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("organisationGroup with id %d not found", id)));
        }
        logger.info("Deleting organisation group {}", organisationGroup.getName());
        organisationGroupRepository.delete(organisationGroup);
        return new ResponseEntity<>(OrganisationGroupContract.fromEntity(organisationGroup), HttpStatus.OK);
    }

    private void saveOrganisationGroup(@RequestBody OrganisationGroupContract request, OrganisationGroup organisationGroup) throws Exception {
        organisationGroup.setName(request.getName());
        organisationGroup.setSchemaName(request.getSchemaName());
        organisationGroup.setAccount(accountRepository.findOne(request.getAccountId()));
        addOrganisationGroupOrganisations(request, organisationGroup, new HashSet<>());
        logger.info("Saving organisation group {}", request.getName());
        organisationGroupRepository.save(organisationGroup);
    }

    private void addOrganisationGroupOrganisations(@RequestBody OrganisationGroupContract request, OrganisationGroup organisationGroup, Set<OrganisationGroupOrganisation> organisationGroupOrganisations) throws Exception {
        for (Long orgId : request.getOrganisationIds()) {
            Organisation organisation = organisationRepository.findOne(orgId);
            if (organisation == null) {
                throw new Exception(String.format("Organisation id %d not found", orgId));
            }
            OrganisationGroupOrganisation organisationGroupOrganisation = new OrganisationGroupOrganisation();
            organisationGroupOrganisation.setName(organisation.getName());
            organisationGroupOrganisation.setOrganisationId(orgId);
            organisationGroupOrganisation.setOrganisationGroup(organisationGroup);
            organisationGroupOrganisations.add(organisationGroupOrganisation);
        }
        organisationGroup.setOrganisationGroupOrganisations(organisationGroupOrganisations);
    }
}
