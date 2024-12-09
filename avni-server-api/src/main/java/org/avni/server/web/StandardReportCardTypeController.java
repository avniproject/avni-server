package org.avni.server.web;

import org.avni.server.dao.StandardReportCardTypeRepository;
import org.avni.server.domain.StandardReportCardType;
import org.avni.server.web.request.StandardReportCardTypeContract;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
}
