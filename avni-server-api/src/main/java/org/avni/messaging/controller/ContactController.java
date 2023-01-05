package org.avni.messaging.controller;

import org.avni.messaging.contract.GroupContactsResponse;
import org.avni.messaging.contract.glific.GlificContactGroupContactsResponse;
import org.avni.messaging.contract.glific.GlificContactGroupsResponse;
import org.avni.messaging.contract.glific.GlificGetGroupResponse;
import org.avni.messaging.repository.GlificContactRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.User;
import org.avni.server.service.IndividualService;
import org.avni.server.service.ObservationService;
import org.avni.server.web.contract.WebPagedResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ContactController {
    private final GlificContactRepository glificContactRepository;
    private final IndividualService individualService;
    private final UserRepository userRepository;

    @Autowired
    public ContactController(GlificContactRepository glificContactRepository, IndividualService individualService, UserRepository userRepository) {
        this.glificContactRepository = glificContactRepository;
        this.individualService = individualService;
        this.userRepository = userRepository;
    }

    @GetMapping("/web/contactGroup")
    public WebPagedResponse getContactGroups(Pageable pageable) {
        List<GlificContactGroupsResponse.ContactGroup> groups = glificContactRepository.getContactGroups(pageable);
        int count = glificContactRepository.getContactGroupCount();
        return new WebPagedResponse(groups, pageable.getPageNumber(), count);
    }

    @GetMapping("/web/contactGroup/{id}")
    public GroupContactsResponse getContactGroupContacts(@PathVariable("id") String id, Pageable pageable) {
        List<GlificContactGroupContactsResponse.GlificContactGroupContacts> contactGroupContacts = glificContactRepository.getContactGroupContacts(id, pageable);
        int count = glificContactRepository.getContactGroupContactsCount(id);
        WebPagedResponse webPagedResponse = new WebPagedResponse(contactGroupContacts, pageable.getPageNumber(), count);
        GlificGetGroupResponse.GlificGroup contactGroup = glificContactRepository.getContactGroup(id);
        return new GroupContactsResponse(webPagedResponse, contactGroup);
    }

    @PostMapping("/web/contactGroup/{id}/subject")
    public void addSubject(@PathVariable("id") String id, @RequestBody long subjectId) {
        String phoneNumber = individualService.findPhoneNumber(subjectId);
        Individual individual = individualService.getIndividual(subjectId);
        glificContactRepository.getOrCreateContact(phoneNumber, individual.getFullName());
    }

    @PostMapping("/web/contactGroup/{id}/user")
    public void addUser(@PathVariable("id") String id, @RequestBody long userId) {
        User user = userRepository.findById(userId).get();
        glificContactRepository.getOrCreateContact(user.getPhoneNumber(), user.getName());
    }
}
