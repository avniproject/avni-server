package org.avni.server.web;

import org.avni.server.dao.AccountRepository;
import org.avni.server.domain.Account;
import org.avni.server.domain.AccountAdmin;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.ReactAdminUtil;
import org.avni.server.web.request.AccountRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.HashSet;
import java.util.Set;

@RestController
public class AccountController implements RestControllerResourceProcessor<Account> {
    private final AccountRepository accountRepository;
    private final AccessControlService accessControlService;

    @Autowired
    public AccountController(AccountRepository accountRepository, AccessControlService accessControlService) {
        this.accountRepository = accountRepository;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/account", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity createAccount(@RequestBody AccountRequest accountRequest) {
        accessControlService.checkIsAdmin();
        if (accountRepository.findByName(accountRequest.getName()) != null) {
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("Account with name %s already exists", accountRequest.getName())));
        }
        Account account = new Account();
        account.setName(accountRequest.getName());
        User user = UserContextHolder.getUserContext().getUser();
        setDefaultAccountAdmin(account, user, new HashSet<>());
        accountRepository.save(account);
        return new ResponseEntity<>(account, HttpStatus.CREATED);
    }

    @RequestMapping(value = "/account/{id}", method = RequestMethod.PUT)
    @Transactional
    public ResponseEntity<?> updateAccount(@PathVariable Long id, @RequestBody AccountRequest accountRequest) {
        accessControlService.checkIsAdmin();
        Account account = accountRepository.findOne(id);
        account.setName(accountRequest.getName());
        accountRepository.save(account);
        return new ResponseEntity<>(account, HttpStatus.OK);
    }

    @RequestMapping(value = {"/account/search/findAll", "/account", "/account/search/find"}, method = RequestMethod.GET)
    public Page<Account> get(@Param("name") String name, Pageable pageable) {
        accessControlService.checkIsAdmin();
        User user = UserContextHolder.getUserContext().getUser();
        if (name != null) {
            return accountRepository.findByAccountAdmin_User_IdAndNameIgnoreCaseContaining(user.getId(), name, pageable);
        } else {
            return accountRepository.findByAccountAdmin_User_Id(user.getId(), pageable);
        }
    }

    @RequestMapping(value = "/account/{id}", method = RequestMethod.GET)
    public Account getById(@PathVariable Long id) {
        accessControlService.checkIsAdmin();
        User user = UserContextHolder.getUserContext().getUser();
        return accountRepository.findByIdAndAccountAdmin_User_Id(id, user.getId());
    }


    private void setDefaultAccountAdmin(Account account, User user, Set<AccountAdmin> accountAdmins) {
        AccountAdmin accountAdmin = new AccountAdmin();
        accountAdmin.setName(user.getName());
        accountAdmin.setUser(user);
        accountAdmin.setAccount(account);
        accountAdmins.add(accountAdmin);
        account.setAccountAdmin(accountAdmins);
    }

}
