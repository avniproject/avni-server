package org.avni.messaging.controller;

import org.avni.messaging.contract.glific.GlificContactGroupContactsResponse;
import org.avni.messaging.contract.glific.GlificContactGroupsResponse;
import org.avni.messaging.repository.GlificContactRepository;
import org.avni.server.web.contract.WebPagedResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GlificController {
    private final GlificContactRepository glificContactRepository;

    @Autowired
    public GlificController(GlificContactRepository glificContactRepository) {
        this.glificContactRepository = glificContactRepository;
    }

    @GetMapping("/web/glificContactGroup")
    public WebPagedResponse getContactGroups(Pageable pageable) {
        List<GlificContactGroupsResponse.ContactGroup> groups = glificContactRepository.getContactGroups(pageable);
        int count = glificContactRepository.getContactGroupCount();
        return new WebPagedResponse(groups, pageable.getPageNumber(), count);
    }

    @GetMapping("/web/glificContactGroup/{id}")
    public WebPagedResponse getContactGroupContacts(@PathVariable("id") String id, Pageable pageable) {
        List<GlificContactGroupContactsResponse.GlificContactGroupContacts> contactGroupContacts = glificContactRepository.getContactGroupContacts(id, pageable);
        int count = glificContactRepository.getContactGroupContactsCount(id);
        return new WebPagedResponse(contactGroupContacts, pageable.getPageNumber(), count);
    }
}
