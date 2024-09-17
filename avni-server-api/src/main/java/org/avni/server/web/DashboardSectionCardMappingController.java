package org.avni.server.web;

import org.avni.server.dao.DashboardSectionCardMappingRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.DashboardSectionCardMapping;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;

@RestController
public class DashboardSectionCardMappingController implements RestControllerResourceProcessor<DashboardSectionCardMapping> {
    private final DashboardSectionCardMappingRepository dashboardSectionCardMappingRepository;

    @Autowired
    public DashboardSectionCardMappingController(DashboardSectionCardMappingRepository dashboardSectionCardMappingRepository) {
        this.dashboardSectionCardMappingRepository = dashboardSectionCardMappingRepository;
    }

    @GetMapping(value = "/v2/dashboardSectionCardMapping/search/lastModified")
    public PagedResources<Resource<DashboardSectionCardMapping>> getDashboardSectionCardMappings(@RequestParam("lastModifiedDateTime")
                                                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                                                                                 @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
                                                                                                 Pageable pageable) {
        return wrap(dashboardSectionCardMappingRepository.findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(lastModifiedDateTime.toDate(),
                CHSEntity.toDate(now), pageable));
    }

    @Override
    public Resource<DashboardSectionCardMapping> process(Resource<DashboardSectionCardMapping> resource) {
        DashboardSectionCardMapping entity = resource.getContent();
        resource.removeLinks();
        resource.add(new Link(entity.getDashboardSection().getUuid(), "dashboardSectionUUID"));
        resource.add(new Link(entity.getCard().getUuid(), "cardUUID"));
        addAuditFields(entity, resource);
        return resource;
    }
}
