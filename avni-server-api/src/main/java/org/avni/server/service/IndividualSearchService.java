package org.avni.server.service;

import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.dao.SubjectSearchRepository;
import org.avni.server.dao.search.SubjectSearchQueryBuilder;
import org.avni.server.projection.SearchSubjectEnrolledProgram;
import org.avni.server.web.request.webapp.search.SubjectSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class IndividualSearchService {
    private final SubjectSearchRepository subjectSearchRepository;
    private final ProgramEnrolmentRepository programEnrolmentRepository;
    private final AddressLevelService addressLevelService;
    private static final Logger logger = Logger.getLogger(IndividualSearchService.class.getName());

    @Autowired
    public IndividualSearchService(SubjectSearchRepository subjectSearchRepository, ProgramEnrolmentRepository programEnrolmentRepository, AddressLevelService addressLevelService) {
        this.subjectSearchRepository = subjectSearchRepository;
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.addressLevelService = addressLevelService;
    }

    public LinkedHashMap<String, Object> search(SubjectSearchRequest subjectSearchRequest) {
        logger.info("Searching for individuals");
        List<Map<String, Object>> searchResults = subjectSearchRepository.search(subjectSearchRequest, new SubjectSearchQueryBuilder());
        logger.info("Found " + searchResults.size() + " individuals");
        Long totalCount = subjectSearchRepository.getTotalCount(subjectSearchRequest, new SubjectSearchQueryBuilder());
        logger.info("Total count of individuals: " + totalCount);
        return constructIndividual(searchResults, totalCount);
    }

    private LinkedHashMap<String, Object> constructIndividual(List<Map<String, Object>> individualList, Long totalCount) {
        LinkedHashMap<String, Object> recordsMap = new LinkedHashMap<>();
        List<Long> individualIds = individualList.stream()
                .map(individualRecord -> Long.valueOf((Integer) individualRecord.get("id")))
                .collect(Collectors.toList());
        List<Long> addressIds = individualList.stream()
                .map(individualRecord -> (Long) individualRecord.get("addressId"))
                .collect(Collectors.toList());

        List<SearchSubjectEnrolledProgram> searchSubjectEnrolledPrograms = !individualIds.isEmpty() ?
                programEnrolmentRepository.findActiveEnrolmentsByIndividualIds(individualIds) :
                Collections.emptyList();

        Map<Long, String> titleLineages = addressLevelService.getTitleLineages(addressIds);

        List<Map<String, Object>> listOfRecords = individualList.stream()
                .peek(individualRecord -> {
                    Long individualId = Long.valueOf((Integer) individualRecord.get("id"));
                    individualRecord.put("enrolments", searchSubjectEnrolledPrograms.stream()
                            .filter(x -> x.getId().equals(individualId))
                            .map(SearchSubjectEnrolledProgram::getProgram)
                            .collect(Collectors.toList()));
                    individualRecord.put("addressLevel", titleLineages.get((Long) individualRecord.get("addressId")));
                }).collect(Collectors.toList());
        recordsMap.put("totalElements", totalCount);
        recordsMap.put("listOfRecords", listOfRecords);
        return recordsMap;
    }

}
