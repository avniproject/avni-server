package org.avni.server.web;

import org.avni.server.dao.DashboardFilterRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.app.dashboard.DashboardFilter;
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
public class DashboardFilterController implements RestControllerResourceProcessor<DashboardFilter> {
    private final DashboardFilterRepository dashboardFilterRepository;

    @Autowired
    public DashboardFilterController(DashboardFilterRepository dashboardFilterRepository) {
        this.dashboardFilterRepository = dashboardFilterRepository;
    }

    @GetMapping(value = "/v2/dashboardFilter/search/lastModified")
    public PagedResources<Resource<DashboardFilter>> getDashboardFilters(@RequestParam("lastModifiedDateTime")
                                                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                                                        @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
                                                                        Pageable pageable) {
        return wrap(dashboardFilterRepository.findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(lastModifiedDateTime.toDate(),
                CHSEntity.toDate(now), pageable));
    }

    @Override
    public Resource<DashboardFilter> process(Resource<DashboardFilter> resource) {
        DashboardFilter entity = resource.getContent();
        resource.removeLinks();
        resource.add(new Link(entity.getDashboard().getUuid(), "dashboardUUID"));
        addAuditFields(entity, resource);
        return resource;
    }
}
