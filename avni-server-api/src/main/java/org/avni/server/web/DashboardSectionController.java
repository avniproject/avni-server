package org.avni.server.web;

import org.avni.server.dao.DashboardSectionRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.DashboardSection;
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
public class DashboardSectionController implements RestControllerResourceProcessor<DashboardSection> {
    private final DashboardSectionRepository dashboardSectionRepository;

    @Autowired
    public DashboardSectionController(DashboardSectionRepository dashboardSectionRepository) {
        this.dashboardSectionRepository = dashboardSectionRepository;
    }

    @GetMapping(value = "/v2/dashboardSection/search/lastModified")
    public PagedResources<Resource<DashboardSection>> getDashboardSection(@RequestParam("lastModifiedDateTime")
                                                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                                                        @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
                                                                        Pageable pageable) {
        return wrap(dashboardSectionRepository.findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(lastModifiedDateTime.toDate(),
                CHSEntity.toDate(now), pageable));
    }

    @Override
    public Resource<DashboardSection> process(Resource<DashboardSection> resource) {
        DashboardSection entity = resource.getContent();
        resource.removeLinks();
        resource.add(new Link(entity.getDashboard().getUuid(), "dashboardUUID"));
        addAuditFields(entity, resource);
        return resource;
    }
}
