package org.avni.server.web;

import org.avni.server.dao.StandardReportCardTypeRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.StandardReportCardType;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.web.request.StandardReportCardTypeContract;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;

@RestController
public class StandardReportCardTypeController implements RestControllerResourceProcessor<StandardReportCardType> {

    private final StandardReportCardTypeRepository standardReportCardTypeRepository;

    @Autowired
    public StandardReportCardTypeController(StandardReportCardTypeRepository standardReportCardTypeRepository) {
        this.standardReportCardTypeRepository = standardReportCardTypeRepository;
    }

    @GetMapping(value = "/web/standardReportCardType")
    @ResponseBody
    public List<StandardReportCardTypeContract> getAll() {
        return standardReportCardTypeRepository.findAllByIsVoidedFalse()
                .stream().map(StandardReportCardTypeContract::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/standardReportCardType/{id}")
    @ResponseBody
    public ResponseEntity<StandardReportCardTypeContract> getById(@PathVariable Long id) {
        Optional<StandardReportCardType> card = standardReportCardTypeRepository.findById(id);
        return card.map(c -> ResponseEntity.ok(StandardReportCardTypeContract.fromEntity(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/v2/standardReportCardType/search/lastModified")
    public PagedResources<Resource<StandardReportCardType>> getStandardReportCardTypes(@RequestParam("lastModifiedDateTime")
                                                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                                                               @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
                                                                               Pageable pageable) {
        return wrap(standardReportCardTypeRepository.findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
                lastModifiedDateTime,
                now, pageable));
    }

    @Override
    public Resource<StandardReportCardType> process(Resource<StandardReportCardType> resource) {
        resource.removeLinks();
        return resource;
    }
}
