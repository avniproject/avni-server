package org.avni.messaging.controller;

import org.avni.messaging.contract.glific.GlificContactGroupsResponse;
import org.avni.messaging.repository.GlificContactRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
    public List<GlificContactGroupsResponse.ContactGroup> getContactGroups() {
        return glificContactRepository.getContactGroups().getGroups();
    }
}
