package org.avni.server.web;

import org.avni.server.dao.CardRepository;
import org.avni.server.domain.ReportCard;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.CardService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.CardContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class ReportCardController {
    private final CardRepository cardRepository;
    private final CardService cardService;
    private final AccessControlService accessControlService;

    @Autowired
    public ReportCardController(CardRepository cardRepository, CardService cardService, AccessControlService accessControlService) {
        this.cardRepository = cardRepository;
        this.cardService = cardService;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/web/reportCard")
    @ResponseBody
    public List<CardContract> getAll() {
        return cardRepository.findAllByIsVoidedFalseOrderByName()
                .stream().map(CardContract::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/reportCard/{id}")
    @ResponseBody
    public ResponseEntity<CardContract> getById(@PathVariable Long id) {
        Optional<ReportCard> card = cardRepository.findById(id);
        return card.map(c -> ResponseEntity.ok(CardContract.fromEntity(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/web/reportCard")
    @ResponseBody
    @Transactional
    public ResponseEntity<CardContract> newCard(@RequestBody CardContract cardContract) {
        accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
        ReportCard card = cardService.saveCard(cardContract);
        return ResponseEntity.ok(CardContract.fromEntity(card));
    }

    @PutMapping(value = "/web/reportCard/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity<CardContract> editCard(@PathVariable Long id, @RequestBody CardContract cardContract) {
        accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
        Optional<ReportCard> card = cardRepository.findById(id);
        if (!card.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        ReportCard savedCard = cardService.editCard(cardContract, id);
        return ResponseEntity.ok(CardContract.fromEntity(savedCard));
    }

    @DeleteMapping(value = "/web/reportCard/{id}")
    @ResponseBody
    @Transactional
    public void deleteCard(@PathVariable Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
        Optional<ReportCard> card = cardRepository.findById(id);
        card.ifPresent(cardService::deleteCard);
    }

}
