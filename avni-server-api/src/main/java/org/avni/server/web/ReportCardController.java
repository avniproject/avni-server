package org.avni.server.web;

import org.avni.server.dao.CardRepository;
import org.avni.server.domain.ReportCard;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.mapper.dashboard.ReportCardMapper;
import org.avni.server.service.CardService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.contract.ReportCardContract;
import org.avni.server.web.request.reports.ReportCardWebRequest;
import org.avni.server.web.response.reports.ReportCardResponse;
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
    public List<ReportCardContract> getAll() {
        return cardRepository.findAllByIsVoidedFalseOrderByName()
                .stream().map(reportCardMapper::toWebResponse)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/reportCard/{id}")
    @ResponseBody
    public ResponseEntity<ReportCardResponse> getById(@PathVariable Long id) {
        Optional<ReportCard> card = cardRepository.findById(id);
        return card.map(c -> ResponseEntity.ok(reportCardMapper.toWebResponse(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/web/reportCard")
    @ResponseBody
    @Transactional
    public ResponseEntity<ReportCardContract> newCard(@RequestBody ReportCardWebRequest cardRequest) {
        accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
        ReportCard card = cardService.saveCard(cardRequest);
        return ResponseEntity.ok(reportCardMapper.toWebResponse(card));
    }

    @PutMapping(value = "/web/reportCard/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity<ReportCardContract> editCard(@PathVariable Long id, @RequestBody ReportCardWebRequest request) {
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
        Optional<ReportCard> card = cardRepository.findById(id);
        card.ifPresent(cardService::deleteCard);
    }

}
