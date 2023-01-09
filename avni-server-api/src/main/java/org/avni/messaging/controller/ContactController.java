package org.avni.messaging.controller;

import org.avni.messaging.contract.ContactGroupRequest;
import org.avni.messaging.contract.GroupContactsResponse;
import org.avni.messaging.contract.glific.GlificContactGroupContactsResponse;
import org.avni.messaging.contract.glific.GlificContactGroupsResponse;
import org.avni.messaging.contract.glific.GlificGetGroupResponse;
import org.avni.messaging.repository.GlificContactRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.User;
import org.avni.server.service.IndividualService;
import org.avni.server.web.contract.WebPagedResponse;
import org.avni.server.web.request.CHSRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ContactController {
    private static final String ContactGroupEndpoint = "/web/contactGroup";
    private final GlificContactRepository glificContactRepository;
    private final IndividualService individualService;
    private final UserRepository userRepository;

    @Autowired
    public ContactController(GlificContactRepository glificContactRepository, IndividualService individualService, UserRepository userRepository) {
        this.glificContactRepository = glificContactRepository;
        this.individualService = individualService;
        this.userRepository = userRepository;
    }

    @GetMapping(ContactGroupEndpoint)
    public WebPagedResponse getContactGroups(Pageable pageable) {
        List<GlificContactGroupsResponse.ContactGroup> groups = glificContactRepository.getContactGroups(pageable);
        int count = glificContactRepository.getContactGroupCount();
        return new WebPagedResponse(groups, pageable.getPageNumber(), count);
    }

    @GetMapping(ContactGroupEndpoint + "/{id}")
    public GroupContactsResponse getContactGroupContacts(@PathVariable("id") String id, Pageable pageable) {
        List<GlificContactGroupContactsResponse.GlificContactGroupContacts> contactGroupContacts = glificContactRepository.getContactGroupContacts(id, pageable);
        int count = glificContactRepository.getContactGroupContactsCount(id);
        WebPagedResponse webPagedResponse = new WebPagedResponse(contactGroupContacts, pageable.getPageNumber(), count);
        GlificGetGroupResponse.GlificGroup contactGroup = glificContactRepository.getContactGroup(id);
        return new GroupContactsResponse(webPagedResponse, contactGroup);
    }

    @PostMapping(ContactGroupEndpoint)
    public void addContactGroup(@RequestBody ContactGroupRequest contactGroupRequest) {
        glificContactRepository.saveContactGroup(contactGroupRequest);
    }

    @PostMapping(ContactGroupEndpoint + "/{contactGroupId}/subject")
    public ResponseEntity<String> addSubject(@PathVariable("contactGroupId") String contactGroupId, @RequestBody CHSRequest subject) {
        String phoneNumber = individualService.findPhoneNumber(subject.getId());
        if (StringUtils.isEmpty(phoneNumber))
           return ResponseEntity.badRequest().body("This subject doesn't have a phone number");
        Individual individual = individualService.getIndividual(subject.getId());
        String contactId = glificContactRepository.getOrCreateContact(phoneNumber, individual.getFullName());
        glificContactRepository.addContactToGroup(contactGroupId, contactId);
        return ResponseEntity.ok("Subject added");
    }

    @PostMapping(ContactGroupEndpoint + "/{contactGroupId}/user")
    public void addUser(@PathVariable("contactGroupId") String contactGroupId, @RequestBody CHSRequest userRequest) {
        User user = userRepository.findById(userRequest.getId()).get();
        String contactId = glificContactRepository.getOrCreateContact(user.getPhoneNumber(), user.getName());
        glificContactRepository.addContactToGroup(contactGroupId, contactId);
    }
}
