package org.avni.server.service;


import org.avni.server.application.Form;
import org.avni.server.application.KeyType;
import org.avni.server.dao.IdentifierAssignmentRepository;
import org.avni.server.dao.IdentifierSourceRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.IdentifierAssignment;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.identifier.IdentifierGenerator;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class IdentifierAssignmentService implements DeviceAwareService {

    private IdentifierSourceRepository identifierSourceRepository;

    private IdentifierAssignmentRepository identifierAssignmentRepository;

    private ApplicationContext context;

    @Autowired
    public IdentifierAssignmentService(IdentifierSourceRepository identifierSourceRepository, IdentifierAssignmentRepository identifierAssignmentRepository, ApplicationContext context) {
        this.identifierSourceRepository = identifierSourceRepository;
        this.identifierAssignmentRepository = identifierAssignmentRepository;
        this.context = context;
    }


    @Transactional
    public void generateIdentifiersIfNecessary(User user, String deviceId) {
        List<IdentifierSource> allAuthorisedIdentifierSources = identifierSourceRepository.getAllAuthorisedIdentifierSources(user.getCatchment());
        for (IdentifierSource identifierSource : allAuthorisedIdentifierSources) {
            generateIdentifiersIfNecessary(user, identifierSource, deviceId);
        }
    }

    @Transactional
    public void generateIdentifiersIfNecessary(User user, IdentifierSource identifierSource, String deviceId) {
        if (shouldGenerateIdentifiers(user, identifierSource, deviceId)) {
            IdentifierGenerator identifierGenerator = context.getBean(identifierSource.getType().name(), IdentifierGenerator.class);
            identifierGenerator.generateIdentifiers(identifierSource, user, deviceId);
        }
    }

    @Transactional
    public List<IdentifierAssignment> generateIdentifiersForAForm(Form form, User user) {
        return form.getApplicableFormElements().stream()
                .filter(formElement -> formElement.getKeyValues().containsKey(KeyType.IdSourceUUID))
                .map(formElement -> {
                    String idSourceUuid = (String) formElement.getKeyValues().getKeyValue(KeyType.IdSourceUUID).getValue();
                    IdentifierSource identifierSource = identifierSourceRepository.findByUuid(idSourceUuid);
                    IdentifierGenerator identifierGenerator = context.getBean(identifierSource.getType().name(), IdentifierGenerator.class);
                    return identifierGenerator.generateSingleIdentifier(identifierSource, user);
                }).collect(Collectors.toList());
    }

    private boolean shouldGenerateIdentifiers(User user, IdentifierSource identifierSource, String deviceId) {
        Integer spareIdentifierAssignments = identifierAssignmentRepository.countIdentifierAssignmentByIdentifierSourceEqualsAndAssignedToEqualsAndIndividualIsNullAndProgramEnrolmentIsNullAndUsedIsFalseAndDeviceIdEquals(identifierSource, user, deviceId);
        return spareIdentifierAssignments < identifierSource.getMinimumBalance();
    }

    @Override
    public boolean isSyncRequiredForDevice(DateTime lastModifiedDateTime, String deviceId) {
        User user = UserContextHolder.getUserContext().getUser();
        this.generateIdentifiersIfNecessary(user, deviceId);
        return identifierAssignmentRepository.existsByAssignedToAndLastModifiedDateTimeGreaterThanAndIsVoidedFalseAndIndividualIsNullAndProgramEnrolmentIsNullAndDeviceIdEquals(user, CHSEntity.toDate(lastModifiedDateTime), deviceId);    }
}
