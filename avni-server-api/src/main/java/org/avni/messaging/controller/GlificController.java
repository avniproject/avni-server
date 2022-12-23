package org.avni.messaging.controller;

import org.avni.messaging.contract.glific.GlificContactGroupsResponse;
import org.avni.messaging.repository.GlificContactRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.GetMapping;
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
    public Page<GlificContactGroupsResponse.ContactGroup> getContactGroups(Pageable pageable) {
        List<GlificContactGroupsResponse.ContactGroup> groups = glificContactRepository.getContactGroups(pageable).getGroups();
//        glific doesn't provide the total count. this means that in our UI the user will incrementally see the number of pages and cannot jump to an arbitrary forward page
        int totalCertainCount = (pageable.getPageNumber() * pageable.getPageSize()) + groups.size();
        PageRequest pageResponse = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.Direction.ASC, "unknown");
        return new PageImpl<>(groups, pageResponse, totalCertainCount + 1);
    }
}
