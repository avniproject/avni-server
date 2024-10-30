package org.avni.server.web;

import org.avni.server.dao.CardRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.ReportCard;
import org.avni.server.domain.StandardReportCardType;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.mapper.dashboard.ReportCardMapper;
import org.avni.server.service.CardService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.reports.ReportCardWebRequest;
import org.avni.server.web.response.reports.ReportCardWebResponse;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;

@RestController
public class ReportCardController implements RestControllerResourceProcessor<ReportCard> {
    private final CardRepository cardRepository;
    private final CardService cardService;
    private final AccessControlService accessControlService;
    private final ReportCardMapper reportCardMapper;

    @Autowired
    public ReportCardController(CardRepository cardRepository, CardService cardService, AccessControlService accessControlService, ReportCardMapper reportCardMapper) {
        this.cardRepository = cardRepository;
        this.cardService = cardService;
        this.accessControlService = accessControlService;
        this.reportCardMapper = reportCardMapper;
    }

    @GetMapping(value = "/web/reportCard")
    @ResponseBody
    public List<ReportCardWebResponse> getAll() {
        return cardRepository.findAllByIsVoidedFalseOrderByName()
                .stream().map(reportCardMapper::toWebResponse)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/reportCard/{id}")
    @ResponseBody
    public ResponseEntity<ReportCardWebResponse> getById(@PathVariable Long id) {
        Optional<ReportCard> card = cardRepository.findById(id);
        return card.map(c -> ResponseEntity.ok(reportCardMapper.toWebResponse(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/web/reportCard")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> newCard(@RequestBody ReportCardWebRequest cardRequest) {
        try {
            accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
            ReportCard card = cardService.saveCard(cardRequest);
            return ResponseEntity.ok(reportCardMapper.toWebResponse(card));
        } catch (BadRequestError e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PutMapping(value = "/web/reportCard/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity<ReportCardWebResponse> editCard(@PathVariable Long id, @RequestBody ReportCardWebRequest request) {
        accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
        Optional<ReportCard> card = cardRepository.findById(id);
        if (!card.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        ReportCard savedCard = cardService.editCard(request, id);
        return ResponseEntity.ok(reportCardMapper.toWebResponse(savedCard));
    }

    @DeleteMapping(value = "/web/reportCard/{id}")
    @ResponseBody
    @Transactional
    public void deleteCard(@PathVariable Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
        cardService.deleteCard(id);
    }

    @GetMapping(value = "/v2/card/search/lastModified")
    public CollectionModel<EntityModel<ReportCard>> getReportCards(@RequestParam("lastModifiedDateTime")
                                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                                                        @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
                                                                        Pageable pageable) {
        return wrap(cardRepository.findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(lastModifiedDateTime.toDate(),
                CHSEntity.toDate(now), pageable));
    }

    @Override
    public EntityModel<ReportCard> process(EntityModel<ReportCard> resource) {
        ReportCard entity = resource.getContent();
        resource.removeLinks();
        StandardReportCardType standardReportCardType = entity.getStandardReportCardType();
        if (standardReportCardType != null)
            resource.add(Link.of(standardReportCardType.getUuid(), "standardReportCardUUID"));
        addAuditFields(entity, resource);
        return resource;
    }
}
