package org.avni.server.importer.batch.csv.creator;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.importer.batch.csv.writer.TxnDataHeaderValidator;
import org.avni.server.importer.batch.csv.writer.header.EncounterHeaderStrategyFactory;
import org.avni.server.importer.batch.csv.writer.header.EncounterHeadersCreator;
import org.avni.server.importer.batch.csv.writer.header.EncounterUploadMode;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.EncounterService;
import org.avni.server.service.UserService;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.util.ValidationUtil;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class EncounterCreator {
    private final EncounterRepository encounterRepository;
    private final BasicEncounterCreator basicEncounterCreator;
    private final FormMappingRepository formMappingRepository;
    private final ObservationCreator observationCreator;
    private final EncounterService encounterService;
    private final EncounterHeaderStrategyFactory strategyFactory;
    private final UserService userService;
    private final SubjectCreator subjectCreator;

    @Autowired
    public EncounterCreator(EncounterRepository encounterRepository,
                            BasicEncounterCreator basicEncounterCreator,
                            FormMappingRepository formMappingRepository,
                            ObservationCreator observationCreator,
                            EncounterService encounterService,
                            EncounterHeaderStrategyFactory strategyFactory,
                            UserService userService,
                            SubjectCreator subjectCreator) {
        this.encounterRepository = encounterRepository;
        this.basicEncounterCreator = basicEncounterCreator;
        this.formMappingRepository = formMappingRepository;
        this.observationCreator = observationCreator;
        this.encounterService = encounterService;
        this.strategyFactory = strategyFactory;
        this.userService = userService;
        this.subjectCreator = subjectCreator;
    }

    public void create(Row row, String encounterUploadMode) throws Exception {
        List<String> allErrorMsgs = new ArrayList<>();
        String legacyId = row.get(EncounterHeadersCreator.ID);
        if (legacyId != null && !legacyId.trim().isEmpty()) {
            Encounter existingEncounter = encounterRepository.findByLegacyIdOrUuid(legacyId);
            if (existingEncounter != null) {
                allErrorMsgs.add(String.format("Entry with id from previous system, %s already present in Avni", legacyId));
                ValidationUtil.handleErrors(allErrorMsgs);
            }
        }
        boolean isScheduledVisit = EncounterUploadMode.isScheduleVisitMode(encounterUploadMode);
        LocalDate encounterDate;
        EncounterUploadMode mode = isScheduledVisit ? EncounterUploadMode.SCHEDULE_VISIT : EncounterUploadMode.UPLOAD_VISIT_DETAILS;

        Encounter encounter = getOrCreateEncounter(row);
        basicEncounterCreator.updateEncounterFields(row, encounter, allErrorMsgs, mode);
        ValidationUtil.handleErrors(allErrorMsgs);

        String subjectId = row.get(EncounterHeadersCreator.SUBJECT_ID);
        Individual subject = subjectCreator.getSubject(subjectId, EncounterHeadersCreator.SUBJECT_ID, allErrorMsgs);
        ValidationUtil.handleErrors(allErrorMsgs);
        encounter.setIndividual(subject);

        FormMapping formMapping = formMappingRepository.getRequiredFormMapping(subject.getSubjectType().getUuid(), null, encounter.getEncounterType().getUuid(), FormType.Encounter);
        if (formMapping == null) {
            throw new Exception(String.format("No form found for the encounter type %s", encounter.getEncounterType().getName()));
        }

        EncounterHeadersCreator encounterHeadersCreator = new EncounterHeadersCreator(strategyFactory);
        TxnDataHeaderValidator.validateHeaders(row.getHeaders(), formMapping, encounterHeadersCreator, mode, allErrorMsgs);
        ValidationUtil.handleErrors(allErrorMsgs);

        if (isScheduledVisit) {
            String earliestDateStr = row.get(EncounterHeadersCreator.EARLIEST_VISIT_DATE);
            String maxDateStr = row.get(EncounterHeadersCreator.MAX_VISIT_DATE);
            if (earliestDateStr == null || earliestDateStr.trim().isEmpty()) {
                allErrorMsgs.add(String.format("'%s' is mandatory for scheduled visits", EncounterHeadersCreator.EARLIEST_VISIT_DATE));
            } else {
                LocalDate earliestDate = DateTimeUtil.parseFlexibleDate(earliestDateStr);
                encounter.setEarliestVisitDateTime(earliestDate != null ? earliestDate.toDateTimeAtStartOfDay() : null);
            }
            if (!StringUtils.hasText(maxDateStr)) {
                allErrorMsgs.add(String.format("'%s' is mandatory for scheduled visits", EncounterHeadersCreator.MAX_VISIT_DATE));
            } else {
                LocalDate maxDate = DateTimeUtil.parseFlexibleDate(maxDateStr);
                if (maxDate != null && maxDate.isBefore(encounter.getEarliestVisitDateTime().toLocalDate())) {
                    allErrorMsgs.add("Max visit date needs to be after Earliest visit date");
                }
                encounter.setMaxVisitDateTime(maxDate != null ? maxDate.toDateTimeAtStartOfDay() : null);
            }
            encounter.setName(encounter.getEncounterType().getName());
        } else {
            String encounterDateStr = row.get(EncounterHeadersCreator.VISIT_DATE);
            if (encounterDateStr == null || encounterDateStr.trim().isEmpty()) {
                allErrorMsgs.add(String.format("'%s' is mandatory for uploaded visits", EncounterHeadersCreator.VISIT_DATE));
            } else {
                encounterDate = DateTimeUtil.parseFlexibleDate(encounterDateStr);
                if (encounterDate.isAfter(LocalDate.now())) {
                    allErrorMsgs.add(String.format("'%s' cannot be in future", EncounterHeadersCreator.VISIT_DATE));
                }
                encounter.setEncounterDateTime(encounterDate.toDateTimeAtStartOfDay(), userService.getCurrentUser());
            }
        }
        ValidationUtil.handleErrors(allErrorMsgs);

        if (mode == EncounterUploadMode.SCHEDULE_VISIT) {
            // For scheduled visits, skip observation validation
            encounter.setObservations(new ObservationCollection());
        } else {
            encounter.setObservations(observationCreator.getObservations(row, encounterHeadersCreator, allErrorMsgs, FormType.Encounter, encounter.getObservations(), formMapping));
        }
        ValidationUtil.handleErrors(allErrorMsgs);
        encounterService.save(encounter);
    }

    private Encounter getOrCreateEncounter(Row row) {
        String id = row.get(EncounterHeadersCreator.ID);
        return createNewEncounter(id);
    }

    private Encounter createNewEncounter(String externalId) {
        Encounter encounter = new Encounter();
        if (StringUtils.hasText(externalId)) {
            encounter.setLegacyId(externalId);
        }
        encounter.setVoided(false);
        encounter.assignUUIDIfRequired();
        return encounter;
    }
}
