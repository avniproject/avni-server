package org.avni.server.service;

import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.dao.SubjectSearchRepository;
import org.avni.server.dao.search.SubjectSearchQueryBuilder;
import org.avni.server.projection.SearchSubjectEnrolledProgram;
import org.avni.server.web.request.webapp.search.SubjectSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IndividualSearchService {
    private final SubjectSearchRepository subjectSearchRepository;
    private final ProgramEnrolmentRepository programEnrolmentRepository;
    private final AddressLevelService addressLevelService;

    @Autowired
    public IndividualSearchService(SubjectSearchRepository subjectSearchRepository, ProgramEnrolmentRepository programEnrolmentRepository, AddressLevelService addressLevelService) {
        this.subjectSearchRepository = subjectSearchRepository;
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.addressLevelService = addressLevelService;
    }

    public LinkedHashMap<String, Object> search(SubjectSearchRequest subjectSearchRequest) {
        List<Map<String, Object>> searchResults = subjectSearchRepository.search(subjectSearchRequest, new SubjectSearchQueryBuilder());
        BigInteger totalCount = subjectSearchRepository.getTotalCount(subjectSearchRequest, new SubjectSearchQueryBuilder());
        return constructIndividual(searchResults, totalCount);
    }

    private LinkedHashMap<String, Object> constructIndividual(List<Map<String, Object>> individualList, BigInteger totalCount) {
        LinkedHashMap<String, Object> recordsMap = new LinkedHashMap<String, Object>();
        List<Long> individualIds = individualList.stream()
                .map(individualRecord -> Long.valueOf((Integer) individualRecord.get("id")))
                .collect(Collectors.toList());
        List<Long> addressIds = individualList.stream()
                .map(individualRecord -> ((BigInteger) individualRecord.get("addressId")).longValue())
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
                    individualRecord.put("addressLevel", titleLineages.get(((BigInteger) individualRecord.get("addressId")).longValue()));
                }).collect(Collectors.toList());
        recordsMap.put("totalElements", totalCount);
        recordsMap.put("listOfRecords", listOfRecords);
        return recordsMap;
    }

}
